import javax.net.ssl.*;
import java.security.KeyStore;
import java.io.File;
public class TestTrustStore {
    public static void main(String[] args) throws Exception {
        String trustStorePath = System.getProperty("javax.net.ssl.trustStore");
        String trustStoreType = System.getProperty("javax.net.ssl.trustStoreType");
        if (trustStorePath == null) {
            trustStorePath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "security" + File.separator + "cacerts";
        }
        if (trustStoreType == null) {
            trustStoreType = KeyStore.getDefaultType();
        }
        System.out.println("trustStore: " + trustStorePath);
        System.out.println("trustStoreType: " + trustStoreType);
        KeyStore ks = KeyStore.getInstance(trustStoreType);
        java.io.FileInputStream fis = new java.io.FileInputStream(trustStorePath);
        ks.load(fis, null);
        System.out.println("Aliases:");
        java.util.Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            System.out.println("  " + aliases.nextElement());
        }
    }
}
