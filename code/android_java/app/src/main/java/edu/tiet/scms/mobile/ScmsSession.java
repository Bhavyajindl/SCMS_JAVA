package edu.tiet.scms.mobile;
import android.app.Application;
import org.json.JSONObject;
/** Credentials and bearer token are kept in memory, never written to source or disk. */
public final class ScmsSession extends Application {
    ScmsApiClient api;
    JSONObject account;
    boolean staff;
    void clear() { if(api!=null)api.logout();api=null;account=null;staff=false; }
}
