package edu.tiet.scms.desktop;
import javafx.application.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.*;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.*;
import javafx.concurrent.Task;
import javafx.stage.Stage;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
/** Real JavaFX desktop inventory client sharing the existing SCMS backend. */
public final class ScmsDesktop extends Application {
 private Stage stage;private DesktopApi api;private int page=1,totalPages=1;
 private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"scms-desktop-http");t.setDaemon(true);return t;});
 private final TableView<EquipmentRow> table=new TableView<>();private final ObservableList<EquipmentRow> rows=FXCollections.observableArrayList();
 private final Label message=new Label(),paging=new Label();private final TextField category=new TextField(),search=new TextField();
 private Button previous,next;
 @Override public void start(Stage stage){this.stage=stage;stage.setTitle("SCMS JavaFX Desktop Client");stage.setMinWidth(850);stage.setMinHeight(600);showLogin();stage.show();}
 private void scene(javafx.scene.Parent root){Scene scene=new Scene(root,1050,720);scene.getRoot().setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 14px; -fx-background-color: #f8fafc;");stage.setScene(scene);}
 private void showLogin(){
  TextField server=new TextField(System.getenv().getOrDefault("SCMS_API_URL","http://127.0.0.1:8000"));server.setPromptText("API server");
  TextField email=new TextField();email.setPromptText("Email address");PasswordField password=new PasswordField();password.setPromptText("Password");Label error=new Label();error.setWrapText(true);error.setStyle("-fx-text-fill: #b91c1c;");
  Button login=new Button("Sign in");login.setDefaultButton(true);login.setMaxWidth(Double.MAX_VALUE);
  login.setOnAction(e->{if(email.getText().isBlank()||password.getText().isBlank()){error.setText("Enter your email and password.");return;}try{api=new DesktopApi(server.getText().trim());}catch(Exception x){error.setText(x.getMessage());return;}String secret=password.getText();password.clear();async(()->{api.login(email.getText(),secret);return true;},ok->{page=1;showInventory();},x->error.setText(x.getMessage()),login);});
  Label title=new Label("SCMS JavaFX Desktop Client");title.setStyle("-fx-font-size: 25px; -fx-font-weight: bold;");
  VBox card=new VBox(14,title,new Label("Staff and administrator inventory access"),new Label("API server"),server,new Label("Email address"),email,new Label("Password"),password,login,error);
  card.setPadding(new Insets(30));card.setMaxWidth(470);card.setStyle("-fx-background-color: white; -fx-border-color: #dbe2ec; -fx-background-radius: 12; -fx-border-radius: 12;");
  StackPane wrapper=new StackPane(card);wrapper.setPadding(new Insets(36));scene(wrapper);
 }
 private void showInventory(){
  table.getColumns().clear();
  column("ID",EquipmentRow::id,60);column("Name",EquipmentRow::name,220);column("Category",EquipmentRow::category,140);column("Status",EquipmentRow::status,140);column("Condition",EquipmentRow::condition,120);column("Location",EquipmentRow::location,180);
  table.setItems(rows);table.setPlaceholder(new Label("No equipment found."));
  category.setPromptText("Category filter");search.setPromptText("Search this page");search.textProperty().addListener((o,a,b)->display());
  Button refresh=new Button("Refresh");refresh.setOnAction(e->{page=1;reload();});category.setOnAction(e->{page=1;reload();});
  Button logout=new Button("Sign out");logout.setOnAction(e->{api.logout();rows.clear();showLogin();});
  HBox header=new HBox(16,new Label("SCMS"),new Label(api.displayName()));Region space=new Region();HBox.setHgrow(space,Priority.ALWAYS);header.getChildren().addAll(space,logout);header.setAlignment(Pos.CENTER_LEFT);
  HBox tools=new HBox(12,category,search,refresh);
  if(api.isAdmin()){Button add=new Button("Add Equipment");add.setOnAction(e->addDialog());tools.getChildren().add(add);}
  previous=new Button("Previous");next=new Button("Next");previous.setOnAction(e->{page--;reload();});next.setOnAction(e->{page++;reload();});
  HBox navigation=new HBox(18,previous,paging,next);navigation.setAlignment(Pos.CENTER);
  Label heading=new Label("Inventory");heading.setStyle("-fx-font-size: 26px; -fx-font-weight: bold;");
  VBox content=new VBox(18,header,heading,new Label("Live equipment records from the same SCMS API"),tools,table,navigation,message);content.setPadding(new Insets(24));VBox.setVgrow(table,Priority.ALWAYS);scene(content);reload();
 }
 private List<EquipmentRow> current=List.of();
 private void column(String label,Function<EquipmentRow,String> value,int width){TableColumn<EquipmentRow,String> col=new TableColumn<>(label);col.setCellValueFactory(c->new ReadOnlyStringWrapper(value.apply(c.getValue())));col.setPrefWidth(width);table.getColumns().add(col);}
 private void display(){String query=search.getText().trim().toLowerCase(Locale.ROOT);rows.setAll(current.stream().filter(r->query.isEmpty()||r.toString().toLowerCase(Locale.ROOT).contains(query)).toList());}
 private void reload(){message.setText("Loading equipment...");int requested=page;String filter=category.getText().trim();async(()->api.inventory(requested,filter),response->{List<EquipmentRow> data=new ArrayList<>();for(var item:response.path("data"))data.add(new EquipmentRow(item.path("id").asText(),item.path("name").asText(),item.path("category").asText(),item.path("status").asText(),item.path("condition").asText(),item.path("location").asText("")));current=List.copyOf(data);totalPages=Math.max(1,response.path("pagination").path("totalPages").asInt(1));display();previous.setDisable(page<=1);next.setDisable(page>=totalPages);paging.setText("Page "+page+" of "+totalPages);message.setText(response.path("pagination").path("total").asInt()+" equipment records");},this::error);}
 private void addDialog(){
  Dialog<ButtonType> dialog=new Dialog<>();dialog.initOwner(stage);dialog.setTitle("Add Equipment");ButtonType save=new ButtonType("Save",ButtonBar.ButtonData.OK_DONE);dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,save);
  TextField name=new TextField(),type=new TextField(),qr=new TextField(),location=new TextField();Label error=new Label();error.setStyle("-fx-text-fill: #b91c1c;");
  VBox fields=new VBox(10,new Label("Equipment name"),name,new Label("Category"),type,new Label("QR identifier"),qr,new Label("Location"),location,error);fields.setPrefWidth(380);dialog.getDialogPane().setContent(fields);
  Button button=(Button)dialog.getDialogPane().lookupButton(save);button.addEventFilter(javafx.event.ActionEvent.ACTION,e->{e.consume();if(name.getText().isBlank()||type.getText().isBlank()||qr.getText().isBlank()){error.setText("Name, category and QR identifier are required.");return;}
   String n=name.getText(),c=type.getText(),q=qr.getText(),l=location.getText();async(()->{api.addEquipment(n,c,q,l);return true;},ok->{dialog.close();reload();},x->error.setText(x.getMessage()),button);});dialog.show();
 }
 private void error(Exception e){if(e instanceof DesktopApi.ApiFailure a&&a.status==401){api.logout();showLogin();return;}message.setText(e.getMessage());}
 private <T> void async(Callable<T> operation,Consumer<T> success,Consumer<Exception> failure,Button... buttons){
  for(Button b:buttons)b.setDisable(true);Task<T> task=new Task<>(){protected T call()throws Exception{return operation.call();}};
  task.setOnSucceeded(e->{for(Button b:buttons)b.setDisable(false);success.accept(task.getValue());});task.setOnFailed(e->{for(Button b:buttons)b.setDisable(false);failure.accept(task.getException() instanceof Exception x?x:new RuntimeException("Request failed."));});worker.submit(task);
 }
 @Override public void stop(){worker.shutdownNow();if(api!=null)api.logout();}
}
