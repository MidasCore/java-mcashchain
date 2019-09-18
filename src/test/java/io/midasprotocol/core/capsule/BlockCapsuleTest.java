package io.midasprotocol.core.capsule;

import com.google.protobuf.ByteString;
import io.midasprotocol.core.Wallet;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.FileUtil;
import io.midasprotocol.common.utils.Sha256Hash;
import io.midasprotocol.core.Constant;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.exception.BadItemException;
import io.midasprotocol.protos.Contract.TransferContract;
import io.midasprotocol.protos.Protocol.Transaction.Contract.ContractType;

import java.io.File;

@Slf4j
public class BlockCapsuleTest {

	private static BlockCapsule blockCapsule0 = new BlockCapsule(1,
		Sha256Hash.wrap(ByteString
			.copyFrom(ByteArray
				.fromHexString("9938a342238077182498b464ac0292229938a342238077182498b464ac029222"))),
		1234,
		ByteString.copyFrom("1234567".getBytes()));
	private static String dbPath = "output_bloackcapsule_test";

	@BeforeClass
	public static void init() {
		Args.setParam(new String[]{"-d", dbPath},
			Constant.TEST_CONF);
	}

	@AfterClass
	public static void removeDb() {
		Args.clearParam();
		FileUtil.deleteDir(new File(dbPath));
	}

	@Test
	public void testCalcMerkleRoot() {
		blockCapsule0.setMerkleRoot();
		Assert.assertEquals(
			Sha256Hash.wrap(Sha256Hash.ZERO_HASH.getByteString()).toString(),
			blockCapsule0.getMerkleRoot().toString());

		logger.info("Transaction[X] Merkle Root : {}", blockCapsule0.getMerkleRoot().toString());

		TransferContract transferContract1 = TransferContract.newBuilder()
			.setAmount(1L)
			.setOwnerAddress(ByteString.copyFrom("0x0000000000000000000".getBytes()))
			.setToAddress(ByteString.copyFrom(
				ByteArray.fromHexString((Wallet.getAddressPreFixString() +
					"A389132D6639FBDA4FBC8B659264E6B7C90DB086"))))
			.build();

		TransferContract transferContract2 = TransferContract.newBuilder()
			.setAmount(2L)
			.setOwnerAddress(ByteString.copyFrom("0x0000000000000000000".getBytes()))
			.setToAddress(ByteString.copyFrom(
				ByteArray.fromHexString(Wallet.getAddressPreFixString() +
					"ED738B3A0FE390EAA71B768B6D02CDBD18FB207B")))
			.build();

		blockCapsule0
			.addTransaction(new TransactionCapsule(transferContract1, ContractType.TransferContract));
		blockCapsule0
			.addTransaction(new TransactionCapsule(transferContract2, ContractType.TransferContract));
		blockCapsule0.setMerkleRoot();

		Assert.assertEquals(
			"0afda51b978fd456a0e790f3498084039712b6e347037d2e8d3656af6f057e41",
			blockCapsule0.getMerkleRoot().toString());

		logger.info("Transaction[O] Merkle Root : {}", blockCapsule0.getMerkleRoot().toString());
	}

  /* @Test
  public void testAddTransaction() {
    TransactionCapsule transactionCapsule = new TransactionCapsule("123", 1L);
    blockCapsule0.addTransaction(transactionCapsule);
    Assert.assertArrayEquals(blockCapsule0.getTransactions().get(0).getHash().getBytes(),
        transactionCapsule.getHash().getBytes());
    Assert.assertEquals(transactionCapsule.getInstance().getRawData().getVout(0).getValue(),
        blockCapsule0.getTransactions().get(0).getInstance().getRawData().getVout(0).getValue());
  } */

	@Test
	public void testGetData() {
		blockCapsule0.getData();
		byte[] b = blockCapsule0.getData();
		BlockCapsule blockCapsule1;
		try {
			blockCapsule1 = new BlockCapsule(b);
			Assert.assertEquals(blockCapsule0.getBlockId(), blockCapsule1.getBlockId());
		} catch (BadItemException e) {
			e.printStackTrace();
		}

	}

	@Test
	public void testValidate() {

	}

	@Test
	public void testGetInsHash() {
		Assert.assertEquals(1,
			blockCapsule0.getInstance().getBlockHeader().getRawData().getNumber());
		Assert.assertEquals(blockCapsule0.getParentHash(),
			Sha256Hash.wrap(blockCapsule0.getParentHashStr()));
	}

	@Test
	public void testGetTimeStamp() {
		Assert.assertEquals(1234L, blockCapsule0.getTimeStamp());
	}

}