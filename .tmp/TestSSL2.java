import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
public class TestSSL2 {
    public static void main(String[] args) throws Exception {
        URL url = new URL("https://dl.google.com");
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        System.out.println("Response code: " + conn.getResponseCode());
    }
}
