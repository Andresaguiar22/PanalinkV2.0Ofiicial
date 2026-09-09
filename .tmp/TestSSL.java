import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
public class TestSSL {
    public static void main(String[] args) throws Exception {
        URL url = new URL("https://plugins.gradle.org");
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        System.out.println("Response code: " + conn.getResponseCode());
    }
}
