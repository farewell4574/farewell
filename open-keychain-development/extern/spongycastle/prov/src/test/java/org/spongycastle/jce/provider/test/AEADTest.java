package org.spongycastle.jce.provider.test;

import java.io.IOException;
import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.InvalidParameterSpecException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.spongycastle.asn1.cms.GCMParameters;
import org.spongycastle.jcajce.spec.RepeatedSecretKeySpec;
import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.spongycastle.util.Arrays;
import org.spongycastle.util.encoders.Hex;
import org.spongycastle.util.test.SimpleTest;

public class AEADTest extends SimpleTest
{

    // EAX test vector from EAXTest
    private byte[] K2 = Hex.decode("91945D3F4DCBEE0BF45EF52255F095A4");
    private byte[] N2 = Hex.decode("BECAF043B0A23D843194BA972C66DEBD");
    private byte[] A2 = Hex.decode("FA3BFD4806EB53FA");
    private byte[] P2 = Hex.decode("F7FB");
    private byte[] C2 = Hex.decode("19DD5C4C9331049D0BDAB0277408F67967E5");
    // C2 with only 64bit MAC (default for EAX)
    private byte[] C2_short = Hex.decode("19DD5C4C9331049D0BDA");

    private byte[] KGCM = Hex.decode("00000000000000000000000000000000");
    private byte[] NGCM = Hex.decode("000000000000000000000000");
    private byte[] CGCM = Hex.decode("58e2fccefa7e3061367f1d57a4e7455a");

    public String getName()
    {
        return "AEAD";
    }

    public void performTest() throws Exception
    {
        boolean aeadAvailable = false;
        try
        {
            this.getClass().getClassLoader().loadClass("javax.crypto.spec.GCMParameterSpec");
            aeadAvailable = true;
        }
        catch (ClassNotFoundException e)
        {
        }
        if (aeadAvailable)
        {
            checkCipherWithAD(K2, N2, A2, P2, C2_short);
            testGCMParameterSpec(K2, N2, A2, P2, C2);
            testGCMParameterSpecWithRepeatKey(K2, N2, A2, P2, C2);
            testGCMGeneric(KGCM, NGCM, new byte[0], new byte[0], CGCM);
            testGCMParameterSpecWithMultipleUpdates(K2, N2, A2, P2, C2);
        }
        else
        {
            System.err.println("GCM AEADTests disabled due to JDK");
        }
        testTampering(aeadAvailable);
    }

    private void testTampering(boolean aeadAvailable)
        throws InvalidKeyException,
        InvalidAlgorithmParameterException,
        NoSuchAlgorithmException,
        NoSuchProviderException,
        NoSuchPaddingException,
        IllegalBlockSizeException,
        BadPaddingException
    {
        Cipher eax = Cipher.getInstance("AES/EAX/NoPadding", "SC");
        final SecretKeySpec key = new SecretKeySpec(new byte[eax.getBlockSize()], eax.getAlgorithm());
        final IvParameterSpec iv = new IvParameterSpec(new byte[eax.getBlockSize()]);

        eax.init(Cipher.ENCRYPT_MODE, key, iv);
        byte[] ciphertext = eax.doFinal(new byte[100]);
        ciphertext[0] = (byte)(ciphertext[0] + 1);  // Tamper

        try
        {
            eax.init(Cipher.DECRYPT_MODE, key, iv);
            eax.doFinal(ciphertext);
            fail("Tampered ciphertext should be invalid");
        }
        catch (BadPaddingException e)
        {
            if (aeadAvailable)
            {
                if (!e.getClass().getName().equals("javax.crypto.AEADBadTagException"))
                {
                    fail("Tampered AEAD ciphertext should fail with AEADBadTagException when available.");
                }
            }
        }
    }

    private void checkCipherWithAD(byte[] K,
                                   byte[] N,
                                   byte[] A,
                                   byte[] P,
                                   byte[] C) throws InvalidKeyException,
            NoSuchAlgorithmException, NoSuchPaddingException,
            IllegalBlockSizeException, BadPaddingException,
            InvalidAlgorithmParameterException, NoSuchProviderException
    {
        Cipher eax = Cipher.getInstance("AES/EAX/NoPadding", "SC");
        SecretKeySpec key = new SecretKeySpec(K, "AES");
        IvParameterSpec iv = new IvParameterSpec(N);
        eax.init(Cipher.ENCRYPT_MODE, key, iv);

        eax.updateAAD(A);
        byte[] c = eax.doFinal(P);

        if (!areEqual(C, c))
        {
            fail("JCE encrypt with additional data failed.");
        }

        eax.init(Cipher.DECRYPT_MODE, key, iv);
        eax.updateAAD(A);
        byte[] p = eax.doFinal(C);

        if (!areEqual(P, p))
        {
            fail("JCE decrypt with additional data failed.");
        }
    }

    private void testGCMParameterSpec(byte[] K,
                                      byte[] N,
                                      byte[] A,
                                      byte[] P,
                                      byte[] C)
        throws InvalidKeyException,
        NoSuchAlgorithmException, NoSuchPaddingException,
        IllegalBlockSizeException, BadPaddingException,
        InvalidAlgorithmParameterException, NoSuchProviderException, IOException
    {
        Cipher eax = Cipher.getInstance("AES/EAX/NoPadding", "SC");
        SecretKeySpec key = new SecretKeySpec(K, "AES");

        // GCMParameterSpec mapped to AEADParameters and overrides default MAC
        // size
        GCMParameterSpec spec = new GCMParameterSpec(128, N);
        eax.init(Cipher.ENCRYPT_MODE, key, spec);

        eax.updateAAD(A);
        byte[] c = eax.doFinal(P);

        if (!areEqual(C, c))
        {
            fail("JCE encrypt with additional data and GCMParameterSpec failed.");
        }

        eax.init(Cipher.DECRYPT_MODE, key, spec);
        eax.updateAAD(A);
        byte[] p = eax.doFinal(C);

        if (!areEqual(P, p))
        {
            fail("JCE decrypt with additional data and GCMParameterSpec failed.");
        }

        AlgorithmParameters algParams = eax.getParameters();

        byte[] encParams = algParams.getEncoded();

        GCMParameters gcmParameters = GCMParameters.getInstance(encParams);

        if (!Arrays.areEqual(spec.getIV(), gcmParameters.getNonce()) || spec.getTLen() != gcmParameters.getIcvLen())
        {
            fail("parameters mismatch");
        }
    }

    private void testGCMParameterSpecWithMultipleUpdates(byte[] K,
                                      byte[] N,
                                      byte[] A,
                                      byte[] P,
                                      byte[] C)
        throws Exception
    {
        Cipher eax = Cipher.getInstance("AES/EAX/NoPadding", "SC");
        SecretKeySpec key = new SecretKeySpec(K, "AES");
        SecureRandom random = new SecureRandom();

        // GCMParameterSpec mapped to AEADParameters and overrides default MAC
        // size
        GCMParameterSpec spec = new GCMParameterSpec(128, N);

        for (int i = 900; i != 1024; i++)
        {
            byte[] message = new byte[i];

            random.nextBytes(message);

            eax.init(Cipher.ENCRYPT_MODE, key, spec);

            byte[] out = new byte[eax.getOutputSize(i)];

            int offSet = 0;

            int count;
            for (count = 0; count < i / 21; count++)
            {
                offSet += eax.update(message, count * 21, 21, out, offSet);
            }

            offSet += eax.doFinal(message, count * 21, i - (count * 21), out, offSet);

            byte[] dec = new byte[i];
            int    len = offSet;

            eax.init(Cipher.DECRYPT_MODE, key, spec);

            offSet = 0;
            for (count = 0; count < len / 10; count++)
            {
                offSet += eax.update(out, count * 10, 10, dec, offSet);
            }

            offSet += eax.doFinal(out, count * 10, len - (count * 10), dec, offSet);

            if (!Arrays.areEqual(message, dec) || offSet != message.length)
            {
                fail("message mismatch");
            }
        }
    }


    private void testGCMParameterSpecWithRepeatKey(byte[] K,
                                                   byte[] N,
                                                   byte[] A,
                                                   byte[] P,
                                                   byte[] C)
        throws InvalidKeyException, NoSuchAlgorithmException,
        NoSuchPaddingException, IllegalBlockSizeException,
        BadPaddingException, InvalidAlgorithmParameterException, NoSuchProviderException, IOException
    {
        Cipher eax = Cipher.getInstance("AES/EAX/NoPadding", "SC");
        SecretKeySpec key = new SecretKeySpec(K, "AES");
        GCMParameterSpec spec = new GCMParameterSpec(128, N);
        eax.init(Cipher.ENCRYPT_MODE, key, spec);

        eax.updateAAD(A);
        byte[] c = eax.doFinal(P);

        if (!areEqual(C, c))
        {
            fail("JCE encrypt with additional data and RepeatedSecretKeySpec failed.");
        }

        // Check GCMParameterSpec handling knows about RepeatedSecretKeySpec
        eax.init(Cipher.DECRYPT_MODE, new RepeatedSecretKeySpec("AES"), spec);
        eax.updateAAD(A);
        byte[] p = eax.doFinal(C);

        if (!areEqual(P, p))
        {
            fail("JCE decrypt with additional data and RepeatedSecretKeySpec failed.");
        }

        AlgorithmParameters algParams = eax.getParameters();

        byte[] encParams = algParams.getEncoded();

        GCMParameters gcmParameters = GCMParameters.getInstance(encParams);

        if (!Arrays.areEqual(spec.getIV(), gcmParameters.getNonce()) || spec.getTLen() != gcmParameters.getIcvLen())
        {
            fail("parameters mismatch");
        }
    }

    private void testGCMGeneric(byte[] K,
                                      byte[] N,
                                      byte[] A,
                                      byte[] P,
                                      byte[] C)
        throws InvalidKeyException,
        NoSuchAlgorithmException, NoSuchPaddingException,
        IllegalBlockSizeException, BadPaddingException,
        InvalidAlgorithmParameterException, NoSuchProviderException, IOException, InvalidParameterSpecException
    {
        Cipher eax = Cipher.getInstance("AES/GCM/NoPadding", "SC");
        SecretKeySpec key = new SecretKeySpec(K, "AES");

        // GCMParameterSpec mapped to AEADParameters and overrides default MAC
        // size
        GCMParameterSpec spec = new GCMParameterSpec(128, N);
        eax.init(Cipher.ENCRYPT_MODE, key, spec);

        eax.updateAAD(A);
        byte[] c = eax.doFinal(P);

        if (!areEqual(C, c))
        {
            fail("JCE encrypt with additional data and GCMParameterSpec failed.");
        }

        eax = Cipher.getInstance("GCM", "SC");
        eax.init(Cipher.DECRYPT_MODE, key, spec);
        eax.updateAAD(A);
        byte[] p = eax.doFinal(C);

        if (!areEqual(P, p))
        {
            fail("JCE decrypt with additional data and GCMParameterSpec failed.");
        }

        AlgorithmParameters algParams = eax.getParameters();

        byte[] encParams = algParams.getEncoded();

        GCMParameters gcmParameters = GCMParameters.getInstance(encParams);

        if (!Arrays.areEqual(spec.getIV(), gcmParameters.getNonce()) || spec.getTLen() != gcmParameters.getIcvLen())
        {
            fail("parameters mismatch");
        }

        GCMParameterSpec gcmSpec = algParams.getParameterSpec(GCMParameterSpec.class);

        if (!Arrays.areEqual(gcmSpec.getIV(), gcmParameters.getNonce()) || gcmSpec.getTLen() != gcmParameters.getIcvLen())
        {
            fail("spec parameters mismatch");
        }

        if (!Arrays.areEqual(eax.getIV(), gcmParameters.getNonce()))
        {
            fail("iv mismatch");
        }
    }

    public static void main(String[] args) throws Exception
    {
        Security.addProvider(new BouncyCastleProvider());

        runTest(new AEADTest());
    }
}
