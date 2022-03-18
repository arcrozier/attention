import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/*
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
}*/
public class SpaceShip {
    private Set<String> passengers = ConcurrentHashmap.newKeySet();
    private int capacity = 50;

    private Lock lock = new ReentrantLock();

    // Returns unique id for spaceship, assume correct implementation
    private int getId();

    public boolean addPassenger(String passenger) {
        lock.acquire();
        if (passengers.size() >= capacity || passengers.contains(passenger)) {
            return false;
        }
        passengers.add(passenger);
        lock.release();
        return true;
    }

    public void removePassenger(String passenger) {
        lock.acquire();
        passengers.remove(passenger);
        lock.release();
    }

    public void increaseCapacity() {
        lock.acquire();
        capacity++;
        lock.release();
    }

    public boolean swapPassengers(String ourPassenger, String otherPassenger, SpaceShip otherShip) {
        if (otherShip.getId() < getId()) {
            otherShip.lock.acquire();
            lock.acquire();
        } else {
            lock.acquire();
            otherShip.lock.acquire();
        }
        if (!passengers.contains(ourPassenger) ||
                !otherShip.passengers.contains(otherPassenger) ||
                passengers.contains(otherPassenger) ||
                otherShip.passengers.contains(ourPassenger)) {
            otherShip.lock.release();
            lock.release();
            return false;
        }

        passengers.add(otherPassenger);
        otherShip.passengers.add(ourPassenger);

        passengers.remove(ourPassenger);
        otherShip.passengers.remove(otherPassenger);

        otherShip.lock.release();
        lock.release();
        return true;
    }
}
