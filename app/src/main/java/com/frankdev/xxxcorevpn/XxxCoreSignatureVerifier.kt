
package com.frankdev.xxxcorevpn

import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

object XxxCoreSignatureVerifier {

    private const val PUBLIC_KEY_BASE64 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArMM/6/8uSgMSuzywaiMpTsEUfk4nnWath5VshrKr1yMdraTllsSW/ZmdpEhdXZgDU4GGaRtl4QAUFQ+EFBKPfsH6g+xhUDusfnP3dAj2d7hDWi3/6oDhLayeEihkUAnlFkDuwOrzxWHMI9KCSVSDOR6DQKKVakb+hgsmFHrino+g0TAunYV5meDuKS+U+WcTU8fN93aDkh21nZ4lqCnsy6goebszNh+wu/1zd0YOoz1grFI3a42xpWkitEMWheBcD2Kh1NCUVlI9gWJOrch3CTCopUEhY5kG/LYTQQQXLD0cDE9Z/jgoe2TowfgH+aF6FXZ+vmhAIndC1cN4VdeBBQIDAQAB"

    fun verify(content: String) {
        val envelope = JSONObject(content)

        if (envelope.optString("format") != "XXXCORE") {
            throw IllegalArgumentException("Format .xxxcore invalide.")
        }

        if (envelope.optInt("version") != 1) {
            throw IllegalArgumentException("Version .xxxcore non prise en charge.")
        }

        if (envelope.optString("signatureAlgorithm") != "SHA256withRSA") {
            throw IllegalArgumentException("Algorithme de signature invalide.")
        }

        val expiry = envelope.getLong("expiresAt")
        if (expiry <= 0L) {
            throw IllegalArgumentException("Date d'expiration absente ou invalide.")
        }

        val signatureText = envelope.getString("signature")
        if (signatureText.isBlank()) {
            throw IllegalArgumentException("Signature numérique absente.")
        }

        val signedEnvelope = JSONObject(envelope.toString())
        signedEnvelope.remove("signature")
        signedEnvelope.remove("signatureAlgorithm")

        val publicKeyBytes = Base64.decode(PUBLIC_KEY_BASE64, Base64.DEFAULT)
        val keySpec = X509EncodedKeySpec(publicKeyBytes)
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(keySpec)

        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(publicKey)
        verifier.update(signedEnvelope.toString().toByteArray(Charsets.UTF_8))

        val signatureBytes = Base64.decode(signatureText, Base64.DEFAULT)

        if (!verifier.verify(signatureBytes)) {
            throw SecurityException("Signature invalide : fichier modifié ou non autorisé.")
        }

        if (System.currentTimeMillis() > expiry) {
            throw SecurityException("Ce fichier .xxxcore a expiré.")
        }
    }
}
