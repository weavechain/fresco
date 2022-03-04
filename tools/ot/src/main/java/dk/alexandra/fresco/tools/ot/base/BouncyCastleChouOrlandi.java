package dk.alexandra.fresco.tools.ot.base;

import dk.alexandra.fresco.framework.network.Network;
import dk.alexandra.fresco.framework.util.Drbg;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.math.ec.ECCurve;

import java.math.BigInteger;
import java.security.Security;

public class BouncyCastleChouOrlandi extends AbstractChouOrlandiOT<BouncyCastleECCElement>{

    /**
     * The modulus of the Diffie-Hellman group used in the OT.
     */
    private final BigInteger dhModulus;
    /**
     * The generator of the Diffie-Hellman group used in the OT.
     */
    private final org.bouncycastle.math.ec.ECPoint dhGenerator;

    private final ECCurve curve;

    @Override
    BouncyCastleECCElement multiplyWithGenerator(BigInteger input) {
        return new BouncyCastleECCElement(this.dhGenerator.multiply(input));
    }

    public BouncyCastleChouOrlandi(int otherId, Drbg randBit, Network network) {
        super(otherId, randBit, network);
        Security.addProvider(new BouncyCastleProvider());
        X9ECParameters ecP = CustomNamedCurves.getByName("curve25519");
        this.curve = ecP.getCurve();
        this.dhModulus = curve.getOrder();
        this.dhGenerator = ecP.getG();
    }

    @Override
    BouncyCastleECCElement decodeElement(byte[] bytes) {
        return new BouncyCastleECCElement(this.curve.decodePoint(bytes));
    }

    @Override
    BouncyCastleECCElement getGenerator() {
        return new BouncyCastleECCElement(this.dhGenerator);
    }

    @Override
    BigInteger getDhModulus() {
        return this.dhModulus;
    }

}
