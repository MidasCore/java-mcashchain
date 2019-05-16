package io.midasprotocol.common.crypto;

import io.midasprotocol.common.utils.Base58;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.Sha256Hash;
import io.midasprotocol.common.utils.StringUtil;
import io.midasprotocol.core.Wallet;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;
import io.midasprotocol.common.crypto.ECKey.ECDSASignature;

import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.SignatureException;

import static org.junit.Assert.*;

@Slf4j
public class ECKeyTest {

	private String privString = "c85ef7d79691fe79573b1a7064c19c1a9819ebdbd1faaab1a8ec92344438aaf4";
	private BigInteger privateKey = new BigInteger(privString, 16);

	private String pubString = "040947751e3022ecf3016be03ec77ab0ce3c2662b4843898cb068d74f698ccc8ad75aa17564ae80a20bb044ee7a6d903e8e8df624b089c95d66a0570f051e5a05b";
	private String compressedPubString = "030947751e3022ecf3016be03ec77ab0ce3c2662b4843898cb068d74f698ccc8ad";
	private byte[] pubKey = Hex.decode(pubString);
	private byte[] compressedPubKey = Hex.decode(compressedPubString);
	private String address = "cd2a3d9f938e13cd947ec05abc7fe734df8dd826";

	@Test
	public void testHashCode() {
		assertEquals(-351262686, ECKey.fromPrivate(privateKey).hashCode());
	}

	@Test
	public void testECKey() {
		ECKey key = new ECKey();
		assertTrue(key.isPubKeyCanonical());
		assertNotNull(key.getPubKey());
		assertNotNull(key.getPrivKeyBytes());
		logger.info(Hex.toHexString(key.getPrivKeyBytes()) + ": Generated privkey");
		logger.info(Hex.toHexString(key.getPubKey()) + ": Generated pubkey");
	}

	public static String encode58Check(byte[] input) {
		byte[] hash0 = Sha256Hash.hash(input);
		byte[] hash1 = Sha256Hash.hash(hash0);
		byte[] inputCheck = new byte[input.length + 4];
		System.arraycopy(input, 0, inputCheck, 0, input.length);
		System.arraycopy(hash1, 0, inputCheck, input.length, 4);
		return Base58.encode(inputCheck);
	}

	@Test
	public void testTest() {
		int prefixLength = 1;
		int byteLength = 20;
		String[] ethAddresses = new String[] {
				"cd2a3d9f938e13cd947ec05abc7fe734df8dd826",
				"e3b71ba0a7543060173bc6e860b901b2c3241652",
				"bc3fa7d86c1ca595fa26f36b1a3397bab4ff849a",
				"12e488654c2e7fc606c268ca19fbfa8d1c6a35da",
				"c8bc5147ebdf52717278460e39c1ab0d8a2e3d27",
				"f849b9a3bb2fd8235e5639b9377338303bac7424",
				"84ca19269c61f4778e51a8ed085620d7ac1fc2ea",
				"2b0c293ff59d17813b11161999b367e012173bd3",
				"db46eb291a46318d29a8a23bdc9dec75ea51ef32",
				"78502ce569750cb26ae1e50f1fe708653cede06c",
				"bd8646fffa2be8bd9019b54f7940d6536ad8a5a0",
				"9aad9b98a2e435707fa39539ea0b12cb4d80468d",
				"bf82fd6597cd3200c468220ecd7cf47c1a4cb149",
				"4536f33f3e0a725484738017c533f877d5df3a82",
				"0000000000000000000000000000000000000000",
				"4e9ce36e442e55ecd9025b9a6e0d88485d628a67",
				"53d284357ec70ce289d6d64134dfac8e511c8a3d",
				"fca70e67b3f93f679992cd36323eeb5a5370c8e4",
				"51f9c432a4e59ac86282d6adab4c2eb8919160eb"
		};
		byte[][] temp = new byte[ethAddresses.length][21];
		for (int i = 0; i < ethAddresses.length; i++) {
			byte[] b = ByteArray.fromHexString(ethAddresses[i]);
			System.arraycopy(b, 0, temp[i], 1, byteLength);
		}
		boolean[] ok = new boolean[255];
		for (int i = 0; i < 255; i++) {
			ok[i] = true;
			for (int id = 0; id < ethAddresses.length; id++) {
				temp[id][0] = (byte) i;
				String base58 = encode58Check(temp[id]);
				if (!base58.startsWith("M")) {
					ok[i] = false;
				}
			}
			if (ok[i]) {
				logger.info("{}", Hex.toHexString(new byte[]{(byte)i}));
			}
		}
	}

	@Test
	public void testB() {
		String hex = "4536f33f3e0a725484738017c533f877d5df3a82";
		String hexAddress = Wallet.getAddressPreFixString() + hex;
		logger.info(hexAddress);
		logger.info(encode58Check(ByteArray.fromHexString(hexAddress)));
	}

	@Test
	public void testC() {
		String base58Address = "MWXz8Wyib9yTptiMXvsfEk9ohUPqqnqVdW";
		byte[] bytes = Wallet.decodeFromBase58Check(base58Address);
		logger.info(StringUtil.createReadableString(bytes));
	}

	@Test
	public void testD() {
		String privKey = "e54c3ba5ecbaa63e4c2ea40f92bb1df9919508d7d880713e16250e4d8687f936";
		BigInteger privateKey = new BigInteger(privKey, 16);
		ECKey key = ECKey.fromPrivate(privateKey);
		logger.info(StringUtil.createReadableString(key.getAddress()));
		logger.info(Wallet.encodeBase58Check(key.getAddress()));
	}

	@Test
	public void testFromPrivateKey() {
		ECKey key = ECKey.fromPrivate(privateKey);
		assertTrue(key.isPubKeyCanonical());
		assertTrue(key.hasPrivKey());
		assertArrayEquals(pubKey, key.getPubKey());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testPrivatePublicKeyBytesNoArg() {
		new ECKey((BigInteger) null, null);
		fail("Expecting an IllegalArgumentException for using only null-parameters");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testInvalidPrivateKey() throws Exception {
		new ECKey(
				Security.getProvider("SunEC"),
				KeyPairGenerator.getInstance("RSA").generateKeyPair().getPrivate(),
				ECKey.fromPublicOnly(pubKey).getPubKeyPoint());
		fail("Expecting an IllegalArgumentException for using an non EC private key");
	}

	@Test
	public void testIsPubKeyOnly() {
		ECKey key = ECKey.fromPublicOnly(pubKey);
		assertTrue(key.isPubKeyCanonical());
		assertTrue(key.isPubKeyOnly());
		assertArrayEquals(key.getPubKey(), pubKey);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSignIncorrectInputSize() {
		ECKey key = new ECKey();
		String message = "The quick brown fox jumps over the lazy dog.";
		ECDSASignature sig = key.doSign(message.getBytes());
		fail("Expecting an IllegalArgumentException for a non 32-byte input");
	}

	@Test(expected = SignatureException.class)
	public void testBadBase64Sig() throws SignatureException {
		byte[] messageHash = new byte[32];
		ECKey.signatureToKey(messageHash, "This is not valid Base64!");
		fail("Expecting a SignatureException for invalid Base64");
	}

	@Test(expected = SignatureException.class)
	public void testInvalidSignatureLength() throws SignatureException {
		byte[] messageHash = new byte[32];
		ECKey.signatureToKey(messageHash, "abcdefg");
		fail("Expecting a SignatureException for invalid signature length");
	}

	@Test
	public void testPublicKeyFromPrivate() {
		byte[] pubFromPriv = ECKey.publicKeyFromPrivate(privateKey, false);
		assertArrayEquals(pubKey, pubFromPriv);
	}

	@Test
	public void testPublicKeyFromPrivateCompressed() {
		byte[] pubFromPriv = ECKey.publicKeyFromPrivate(privateKey, true);
		assertArrayEquals(compressedPubKey, pubFromPriv);
	}

	@Test
	public void testGetAddress() {
		ECKey key = ECKey.fromPublicOnly(pubKey);
		// Addresses are prefixed with a constant.
		byte[] genAddress = key.getAddress();
		assertArrayEquals(Hex.decode(address), genAddress);
	}

	@Test
	public void testGetAddressFromPrivateKey() {
		ECKey key = ECKey.fromPrivate(privateKey);
		// Addresses are prefixed with a constant.
		byte[] genAddress = key.getAddress();
		assertArrayEquals(Hex.decode(address), genAddress);
	}

	@Test
	public void testToString() {
		ECKey key = ECKey.fromPrivate(BigInteger.TEN); // An example private key.
		assertEquals(
				"pub:04a0434d9e47f3c86235477c7b1ae6ae5d3442d49b1943c2b752a68e2a47e247c7893aba425419bc27a3b6c7e693a24c696f794c2ed877a1593cbee53b037368d7",
				key.toString());
	}

	@Test
	public void testIsPubKeyCanonicalCorect() {
		// Test correct prefix 4, right length 65
		byte[] canonicalPubkey1 = new byte[65];
		canonicalPubkey1[0] = 0x04;
		assertTrue(ECKey.isPubKeyCanonical(canonicalPubkey1));
		// Test correct prefix 2, right length 33
		byte[] canonicalPubkey2 = new byte[33];
		canonicalPubkey2[0] = 0x02;
		assertTrue(ECKey.isPubKeyCanonical(canonicalPubkey2));
		// Test correct prefix 3, right length 33
		byte[] canonicalPubkey3 = new byte[33];
		canonicalPubkey3[0] = 0x03;
		assertTrue(ECKey.isPubKeyCanonical(canonicalPubkey3));
	}

	@Test
	public void testIsPubKeyCanonicalWrongLength() {
		// Test correct prefix 4, but wrong length !65
		byte[] nonCanonicalPubkey1 = new byte[64];
		nonCanonicalPubkey1[0] = 0x04;
		assertFalse(ECKey.isPubKeyCanonical(nonCanonicalPubkey1));
		// Test correct prefix 2, but wrong length !33
		byte[] nonCanonicalPubkey2 = new byte[32];
		nonCanonicalPubkey2[0] = 0x02;
		assertFalse(ECKey.isPubKeyCanonical(nonCanonicalPubkey2));
		// Test correct prefix 3, but wrong length !33
		byte[] nonCanonicalPubkey3 = new byte[32];
		nonCanonicalPubkey3[0] = 0x03;
		assertFalse(ECKey.isPubKeyCanonical(nonCanonicalPubkey3));
	}

	@Test
	public void testIsPubKeyCanonicalWrongPrefix() {
		// Test wrong prefix 4, right length 65
		byte[] nonCanonicalPubkey4 = new byte[65];
		assertFalse(ECKey.isPubKeyCanonical(nonCanonicalPubkey4));
		// Test wrong prefix 2, right length 33
		byte[] nonCanonicalPubkey5 = new byte[33];
		assertFalse(ECKey.isPubKeyCanonical(nonCanonicalPubkey5));
		// Test wrong prefix 3, right length 33
		byte[] nonCanonicalPubkey6 = new byte[33];
		assertFalse(ECKey.isPubKeyCanonical(nonCanonicalPubkey6));
	}

	@Test
	public void testGetPrivKeyBytes() {
		ECKey key = new ECKey();
		assertNotNull(key.getPrivKeyBytes());
		assertEquals(32, key.getPrivKeyBytes().length);
	}

	@Test
	public void testEqualsObject() {
		ECKey key0 = new ECKey();
		ECKey key1 = ECKey.fromPrivate(privateKey);
		ECKey key2 = ECKey.fromPrivate(privateKey);

		assertFalse(key0.equals(key1));
		assertTrue(key1.equals(key1));
		assertTrue(key1.equals(key2));
	}

	@Test
	public void decryptAECSIC() {
		ECKey key = ECKey.fromPrivate(
				Hex.decode("abb51256c1324a1350598653f46aa3ad693ac3cf5d05f36eba3f495a1f51590f"));
		byte[] payload = key.decryptAES(Hex.decode("84a727bc81fa4b13947dc9728b88fd08"));
		System.out.println(Hex.toHexString(payload));
	}

	@Test
	public void testNodeId() {
		ECKey key = ECKey.fromPublicOnly(pubKey);

		assertEquals(key, ECKey.fromNodeId(key.getNodeId()));
	}
}