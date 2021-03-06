package org.spongycastle.openpgp.operator;

import java.io.IOException;
import java.math.BigInteger;

import org.spongycastle.bcpg.ContainedPacket;
import org.spongycastle.bcpg.MPInteger;
import org.spongycastle.bcpg.PublicKeyEncSessionPacket;
import org.spongycastle.openpgp.PGPException;
import org.spongycastle.openpgp.PGPPublicKey;

public abstract class PublicKeyKeyEncryptionMethodGenerator
    extends PGPKeyEncryptionMethodGenerator
{
    private PGPPublicKey pubKey;
    private boolean hiddenRecipients;

    protected PublicKeyKeyEncryptionMethodGenerator(
            PGPPublicKey pubKey)
    {
        this(pubKey, false);
    }

    protected PublicKeyKeyEncryptionMethodGenerator(
        PGPPublicKey pubKey, boolean hiddenRecipients)
    {
        this.pubKey = pubKey;
        this.hiddenRecipients = hiddenRecipients;

        switch (pubKey.getAlgorithm())
        {
        case PGPPublicKey.RSA_ENCRYPT:
        case PGPPublicKey.RSA_GENERAL:
            break;
        case PGPPublicKey.ELGAMAL_ENCRYPT:
        case PGPPublicKey.ELGAMAL_GENERAL:
            break;
        case PGPPublicKey.ECDH:
            break;
        case PGPPublicKey.DSA:
            throw new IllegalArgumentException("Can't use DSA for encryption.");
        case PGPPublicKey.ECDSA:
            throw new IllegalArgumentException("Can't use ECDSA for encryption.");
        default:
            throw new IllegalArgumentException("unknown asymmetric algorithm: " + pubKey.getAlgorithm());
        }
    }

    public byte[][] processSessionInfo(
        byte[] encryptedSessionInfo)
        throws PGPException
    {
        byte[][] data;

        switch (pubKey.getAlgorithm())
        {
        case PGPPublicKey.RSA_ENCRYPT:
        case PGPPublicKey.RSA_GENERAL:
            data = new byte[1][];

            data[0] = convertToEncodedMPI(encryptedSessionInfo);
            break;
        case PGPPublicKey.ELGAMAL_ENCRYPT:
        case PGPPublicKey.ELGAMAL_GENERAL:
            byte[] b1 = new byte[encryptedSessionInfo.length / 2];
            byte[] b2 = new byte[encryptedSessionInfo.length / 2];

            System.arraycopy(encryptedSessionInfo, 0, b1, 0, b1.length);
            System.arraycopy(encryptedSessionInfo, b1.length, b2, 0, b2.length);

            data = new byte[2][];
            data[0] = convertToEncodedMPI(b1);
            data[1] = convertToEncodedMPI(b2);
            break;
        case PGPPublicKey.ECDH:
            data = new byte[1][];

            data[0] = encryptedSessionInfo;
            break;
        default:
            throw new PGPException("unknown asymmetric algorithm: " + pubKey.getAlgorithm());
        }

        return data;
    }

    private byte[] convertToEncodedMPI(byte[] encryptedSessionInfo)
        throws PGPException
    {
        try
        {
            return new MPInteger(new BigInteger(1, encryptedSessionInfo)).getEncoded();
        }
        catch (IOException e)
        {
            throw new PGPException("Invalid MPI encoding: " + e.getMessage(), e);
        }
    }

    public ContainedPacket generate(int encAlgorithm, byte[] sessionInfo)
        throws PGPException
    {
        long keyId = hiddenRecipients ? 0L : pubKey.getKeyID();
        return new PublicKeyEncSessionPacket(keyId, pubKey.getAlgorithm(), processSessionInfo(encryptSessionInfo(pubKey, sessionInfo)));
    }

    abstract protected byte[] encryptSessionInfo(PGPPublicKey pubKey, byte[] sessionInfo)
        throws PGPException;
}
