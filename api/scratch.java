import java.util.*;
import java.security.*;

public class MyClass {
    public static void main(String args[]) {
        KeyPairGenerator generator;
        try {
            generator = KeyPairGenerator.getInstance("EC");
        } catch (NoSuchAlgorithmException e) {
            System.out.println("No such algorithm");
            return;
        }
        

        KeyPair keyPair = null;
        while (keyPair == null) {
            keyPair = generator.genKeyPair();  // automatically added to key store
        }
        
        System.out.println(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        Signature signature;
        try {
            signature = Signature.getInstance("SHA256withECDSA");
        } catch (NoSuchAlgorithmException e) {
            System.out.println("No such algorithm - SHA256withECDSA");
            return;
        }
        byte[] signed;
        try {
            signature.initSign(keyPair.getPrivate());
            signature.update("c~2:sdnzROtT[-Wi".getBytes());
            signed = signature.sign();
        } catch (InvalidKeyException | SignatureException e) {
            System.out.println("Something went wrong with signature");
            return;
        }
        String signedStr = Base64.getEncoder().encodeToString(signed);
        System.out.println(signedStr);
    }
}