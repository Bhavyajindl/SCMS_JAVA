package edu.tiet.scms.mobile;

import android.app.*;
import android.os.Bundle;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import org.json.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Separate Java implementation of SCMS mobile flows, not a wrapper around Flutter. */
public final class MainActivity extends Activity {
  private ScmsSession session;
  private LinearLayout content,navigation;
  private TextView header,message;
  private int viewVersion;
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private static final int NAVY=Color.rgb(26,35,126),ORANGE=Color.rgb(245,124,0);
  private interface Work<T> { T run() throws Exception; }
  private interface Result<T> { void accept(T value) throws Exception; }

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    setContentView(R.layout.activity_main);
    session=(ScmsSession)getApplication();content=findViewById(R.id.content);navigation=findViewById(R.id.navigation);
    header=findViewById(R.id.header);message=findViewById(R.id.message);
    if(session.account==null)login();else home();
  }
  @Override protected void onDestroy() { viewVersion++;worker.shutdownNow();super.onDestroy(); }
  @Override public void onBackPressed() { if(session.account==null)super.onBackPressed();else home(); }
  private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
  private void screen(String title) {
    viewVersion++;header.setText(title);content.removeAllViews();navigation.removeAllViews();message.setVisibility(View.GONE);
    if(session.account!=null) {
      nav("Home",this::home);
      nav(session.staff?"Inventory":"Equipment",()->equipment(1));
      nav(session.staff?"Queue":"Bookings",()->{if(session.staff)queue();else bookings(1);});
      if(!session.staff)nav("My QR",this::studentQr);
      nav("Profile",this::profile);
    }
  }
  private void nav(String title,Runnable action) { Button b=new Button(this);b.setText(title);b.setOnClickListener(v->action.run());navigation.addView(b); }
  private TextView label(LinearLayout parent,String text,int size,boolean bold) {
    TextView view=new TextView(this);view.setText(text);view.setTextSize(size);view.setTextColor(Color.rgb(28,28,30));
    if(bold)view.setTypeface(null,Typeface.BOLD);view.setPadding(0,dp(6),0,dp(6));parent.addView(view);return view;
  }
  private TextView label(String text) { return label(content,text,16,false); }
  private EditText field(LinearLayout parent,String hint,boolean secret) {
    label(parent,hint,14,true);EditText view=new EditText(this);view.setSingleLine(true);view.setHint(hint);
    if(secret)view.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
    parent.addView(view,new LinearLayout.LayoutParams(-1,dp(52)));return view;
  }
  private Button button(LinearLayout parent,String title,Runnable action) {
    Button view=new Button(this);view.setText(title);view.setTextColor(NAVY);view.setOnClickListener(v->action.run());parent.addView(view);return view;
  }
  private Button button(String title,Runnable action) { return button(content,title,action); }
  private LinearLayout card(String title) {
    LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(10),dp(16),dp(10));
    GradientDrawable background=new GradientDrawable();background.setColor(Color.WHITE);background.setCornerRadius(dp(12));background.setStroke(dp(1),Color.rgb(225,228,236));box.setBackground(background);
    LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(0,dp(8),0,dp(8));content.addView(box,params);
    label(box,title,19,true);return box;
  }
  private void alert(String title,String detail) { new AlertDialog.Builder(this).setTitle(title).setMessage(detail).setPositiveButton("OK",null).show(); }
  private void confirm(String text,Runnable action) { new AlertDialog.Builder(this).setMessage(text).setNegativeButton("Cancel",null).setPositiveButton("Confirm",(d,w)->action.run()).show(); }
  private void failure(Exception error) {
    if(error instanceof ScmsApiClient.ApiException&&((ScmsApiClient.ApiException)error).status==401) { session.clear();login(); }
    message.setText(error.getMessage()==null?"Could not complete the request.":error.getMessage());message.setVisibility(View.VISIBLE);
  }
  private <T> void request(Work<T> operation,Result<T> success,Button... busy) {
    int version=viewVersion;for(Button b:busy)b.setEnabled(false);
    message.setText("Loading...");message.setTextColor(NAVY);message.setVisibility(View.VISIBLE);
    worker.execute(()->{
      try {
        T result=operation.run();
        runOnUiThread(()->{if(isFinishing()||version!=viewVersion)return;for(Button b:busy)b.setEnabled(true);message.setVisibility(View.GONE);
          try { success.accept(result); } catch(Exception e) { message.setTextColor(Color.RED);failure(e); }});
      } catch(Exception e) {
        runOnUiThread(()->{if(isFinishing()||version!=viewVersion)return;for(Button b:busy)b.setEnabled(true);message.setTextColor(Color.RED);failure(e);});
      }
    });
  }
  private void login() {
    screen("SCMS - Sign in");
    label(content,"Sports Complex Management System",24,true);
    label("Separate native Java client using the SCMS API.");
    EditText origin=field(content,"API server URL",false);
    origin.setText(getPreferences(MODE_PRIVATE).getString("api_origin","http://10.0.2.2:18001"));
    label("Phone: use the laptop Wi-Fi IP. Port 18001 is the Java API; port 8000 is the original API.");
    EditText email=field(content,"Email address",false);email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
    EditText password=field(content,"Password",true);
    Spinner type=new Spinner(this);type.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Student","Staff / Admin"}));content.addView(type);
    Button signIn=button("Sign in",()->{});
    signIn.setOnClickListener(v->{
      try {
        ScmsApiClient api=new ScmsApiClient(origin.getText().toString());boolean staff=type.getSelectedItemPosition()==1;
        String address=origin.getText().toString(),mail=email.getText().toString(),secret=password.getText().toString();password.setText("");
        request(()->api.login(mail,secret,staff),account->{session.api=api;session.account=account;session.staff=staff;getPreferences(MODE_PRIVATE).edit().putString("api_origin",address).apply();home();},signIn);
      } catch(Exception e) { failure(e); }
    });
    button("Create student account",()->{try { register(new ScmsApiClient(origin.getText().toString())); }catch(Exception e) { failure(e); }});
  }
  private void register(ScmsApiClient api) {
    screen("Create student account");
    String[] keys={"name","student_id","email","department","password"},titles={"Full name","Student roll number","Email","Department","Password (8+ characters)"};
    EditText[] fields=new EditText[keys.length];for(int i=0;i<keys.length;i++)fields[i]=field(content,titles[i],i==4);
    Button submit=button("Register",()->{});
    submit.setOnClickListener(v->{try {
      JSONObject form=new JSONObject();for(int i=0;i<keys.length;i++)form.put(keys[i],fields[i].getText().toString());
      fields[4].setText("");request(()->api.register(form),account->{session.api=api;session.account=account;session.staff=false;home();},submit);
    } catch(Exception e) { failure(e); }});
    button("Back to sign in",this::login);
  }
  private void home() {
    screen("SCMS");label(content,"Welcome, "+session.account.optString("name"),24,true);
    label("Quick Actions");
    button(session.staff?"Browse Inventory":"Browse Equipment",()->equipment(1));
    if(session.staff) { button("Issue / Return Queue",this::queue);button("Validate QR identifier",this::validateQr); }
    else {
      button("My Bookings",()->bookings(1));button("My QR Code",this::studentQr);button("Usage History",()->history(1));button("Notifications",()->notifications(1));
      request(()->session.api.get("/students/usage-stats").getJSONObject("data"),stats->{
        LinearLayout box=card("Usage");
        label(box,"Last 7 days: "+stats.optInt("sessions_last_7_days")+" sessions",16,false);
        label(box,"Total: "+stats.optInt("total_sessions")+" sessions",16,false);
      });
    }
  }
  private void pages(JSONObject response,int page,Runnable previous,Runnable next) {
    JSONObject pagination=response.optJSONObject("pagination");if(pagination==null)return;
    label("Page "+page+" | "+pagination.optInt("total")+" records");
    if(page>1)button("Previous",previous);
    if(page<pagination.optInt("totalPages",1))button("Next",next);
  }
  private void equipment(int page) {
    screen(session.staff?"Inventory":"Equipment");
    String path=(session.staff?"/inventory":"/equipment")+"?page="+page+"&limit=20";
    request(()->session.api.get(path),response->{
      JSONArray rows=response.getJSONArray("data");if(rows.length()==0)label("No equipment found.");
      for(int i=0;i<rows.length();i++) {
        JSONObject item=rows.getJSONObject(i);LinearLayout box=card(item.optString("name"));
        label(box,item.optString("category")+" | "+item.optString("status"),16,false);
        label(box,"Condition: "+item.optString("condition")+" | Location: "+item.optString("location",""),14,false);
        if(!session.staff)button(box,"View available slots",()->availability(item));
      }
      pages(response,page,()->equipment(page-1),()->equipment(page+1));
    });
  }
  private void availability(JSONObject equipment) {
    screen(equipment.optString("name"));
    request(()->session.api.get("/equipment/"+equipment.optLong("id")+"/availability").getJSONObject("data"),response->{
      JSONArray slots=response.getJSONArray("available_slots");if(slots.length()==0)label("No upcoming slots.");
      for(int i=0;i<slots.length();i++) {
        JSONObject slot=slots.getJSONObject(i);LinearLayout box=card(slot.optString("date"));
        label(box,slot.optString("start_time")+" - "+slot.optString("end_time")+" UTC",16,false);
        label(box,"Status: "+slot.optString("status")+" | Places: "+slot.optInt("available_count"),14,false);
        if(slot.optString("status").equals("OPEN")) {
          Button book=button(box,"Request booking",()->{});
          book.setOnClickListener(v->confirm("Request this slot? Allocation is based on Java fairness rules.",()->request(()->session.api.book(slot.getLong("slot_id")),r->{alert("Booking requested","Your request was saved.");bookings(1);},book)));
        }
      }
    });
  }
  private void bookings(int page) {
    screen("My Bookings");
    request(()->session.api.get("/bookings?page="+page+"&limit=20"),response->{
      JSONArray rows=response.getJSONArray("data");if(rows.length()==0)label("No bookings yet.");
      for(int i=0;i<rows.length();i++) {
        JSONObject booking=rows.getJSONObject(i),eq=booking.optJSONObject("equipment"),slot=booking.optJSONObject("slot");
        LinearLayout box=card(eq==null?"Booking #"+booking.optLong("id"):eq.optString("name"));
        String status=booking.optString("status");label(box,"Status: "+status,16,true);
        if(slot!=null)label(box,slot.optString("date")+" | "+slot.optString("start_time")+" UTC",14,false);
        label(box,"Priority: "+booking.optString("priority_score","pending")+" | Queue: "+booking.optString("queue_position","-"),14,false);
        if(Arrays.asList("REQUESTED","CONFIRMED","WAITLISTED").contains(status)) {
          Button cancel=button(box,"Cancel booking",()->{});
          cancel.setOnClickListener(v->confirm("Cancel this booking?",()->request(()->session.api.cancel(booking.getLong("id")),r->bookings(page),cancel)));
        }
      }
      pages(response,page,()->bookings(page-1),()->bookings(page+1));
    });
  }
  private void history(int page) {
    screen("Usage History");
    request(()->session.api.get("/transactions?page="+page+"&limit=20"),response->{
      JSONArray rows=response.getJSONArray("data");if(rows.length()==0)label("No transactions yet.");
      for(int i=0;i<rows.length();i++) {
        JSONObject txn=rows.getJSONObject(i);LinearLayout box=card(txn.optString("equipment_name","Equipment"));
        label(box,txn.optString("status")+" | Issued: "+txn.optString("issued_at"),15,false);
        label(box,"Due: "+txn.optString("due_at"),14,false);
        if(!txn.isNull("damage_report"))label(box,"Damage: "+txn.optString("damage_report"),14,false);
      }
      pages(response,page,()->history(page-1),()->history(page+1));
    });
  }
  private void studentQr() {
    screen("My QR Code");
    request(()->session.api.get("/students/qr").getJSONObject("data").getString("qr_identifier"),token->{
      var matrix=new QRCodeWriter().encode(token,BarcodeFormat.QR_CODE,600,600);
      Bitmap bitmap=Bitmap.createBitmap(600,600,Bitmap.Config.RGB_565);
      for(int y=0;y<600;y++)for(int x=0;x<600;x++)bitmap.setPixel(x,y,matrix.get(x,y)?Color.BLACK:Color.WHITE);
      ImageView image=new ImageView(this);image.setImageBitmap(bitmap);image.setContentDescription("SCMS student QR code");image.setAdjustViewBounds(true);content.addView(image,new LinearLayout.LayoutParams(-1,dp(280)));
      label("Show this code to staff for equipment issue or return.");TextView identifier=label(token);identifier.setTextIsSelectable(true);
    });
  }
  private void notifications(int page) {
    screen("Notifications");
    button("Mark all as read",()->request(()->session.api.request("POST","/notifications/mark-all-read",null),r->notifications(page)));
    request(()->session.api.get("/notifications?page="+page+"&limit=20"),response->{
      JSONArray rows=response.getJSONArray("data");if(rows.length()==0)label("No notifications.");
      for(int i=0;i<rows.length();i++) {
        JSONObject n=rows.getJSONObject(i);LinearLayout box=card(n.optString("title"));label(box,n.optString("message"),15,false);
        if(!n.optBoolean("is_read"))button(box,"Mark read",()->request(()->session.api.request("PUT","/notifications/"+n.getLong("id")+"/read",null),r->notifications(page)));
      }
      pages(response,page,()->notifications(page-1),()->notifications(page+1));
    });
  }
  private void queue() {
    screen("Staff Issue / Return");
    EditText date=field(content,"Queue date (YYYY-MM-DD, UTC)",false);date.setText(java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString());
    button("Load selected date",()->loadQueue(date.getText().toString()));
    button("Validate a QR identifier",this::validateQr);
    loadQueue(date.getText().toString());
  }
  private void loadQueue(String date) {
    request(()->session.api.get("/staff/queue?for_date="+URLEncoder.encode(date,StandardCharsets.UTF_8.toString())).getJSONObject("data"),response->{
      // Replacing the content prevents duplicate rows on refresh.
      content.removeAllViews();label(content,"Queue for "+date,20,true);button("Choose another date",this::queue);button("Validate QR identifier",this::validateQr);
      JSONArray confirmed=response.getJSONArray("confirmed_bookings_today"),open=response.getJSONArray("open_transactions");
      label("Confirmed bookings: "+confirmed.length());
      for(int i=0;i<confirmed.length();i++) {
        JSONObject booking=confirmed.getJSONObject(i),student=booking.optJSONObject("student"),eq=booking.getJSONObject("equipment");
        LinearLayout box=card(eq.optString("name"));label(box,student==null?"Student":student.optString("name"),16,false);
        Button issue=button(box,"Issue equipment",()->{});
        issue.setOnClickListener(v->confirm("Issue "+eq.optString("name")+" to this student?",()->request(()->session.api.issue(booking.getLong("booking_id"),eq.getLong("id")),r->queue(),issue)));
      }
      label("Open transactions: "+open.length());
      for(int i=0;i<open.length();i++) {
        JSONObject txn=open.getJSONObject(i),eq=txn.optJSONObject("equipment"),student=txn.optJSONObject("student");
        LinearLayout box=card(eq==null?"Equipment":eq.optString("name"));
        label(box,(student==null?"Student":student.optString("name"))+" | "+txn.optString("status"),16,false);
        label(box,"Due: "+txn.optString("due_at"),14,false);button(box,"Process return",()->returnForm(txn.optLong("transaction_id")));
      }
    });
  }
  private void returnForm(long id) {
    screen("Return equipment");
    Spinner condition=new Spinner(this);condition.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"GOOD","FAIR","DAMAGED"}));content.addView(condition);
    EditText report=field(content,"Damage report (required when DAMAGED)",false);
    Button save=button("Confirm return",()->{});
    save.setOnClickListener(v->{String state=condition.getSelectedItem().toString(),detail=report.getText().toString();
      if(state.equals("DAMAGED")&&detail.trim().isEmpty()) { alert("Damage report required","Describe the damage before saving.");return; }
      confirm("Record this equipment return?",()->request(()->session.api.returnItem(id,state,detail),r->queue(),save));
    });
  }
  private void validateQr() {
    screen("Validate QR identifier");
    label("Paste the student token or equipment QR identifier. This preview does not include a camera scanner.");
    EditText identifier=field(content,"QR identifier",false);
    button("Validate student",()->{String value=identifier.getText().toString();request(()->session.api.request("POST","/qr/validate/student",ScmsApiClient.json("token",value)).getJSONObject("data"),r->alert("Student validation",r.toString(2)));});
    button("Validate equipment",()->{String value=identifier.getText().toString();request(()->session.api.request("POST","/qr/validate/equipment",ScmsApiClient.json("qr_code",value)).getJSONObject("data"),r->alert("Equipment validation",r.toString(2)));});
  }
  private void profile() {
    screen("My Profile");
    for(String key:new String[]{"name","email","student_id","department","role","status"})if(session.account.has(key))label(key.replace('_',' ')+": "+session.account.optString(key));
    if(!session.staff) { button("Usage History",()->history(1));button("Notifications",()->notifications(1)); }
    button("Sign out",()->confirm("Sign out?",()->{session.clear();login();}));
  }
}
