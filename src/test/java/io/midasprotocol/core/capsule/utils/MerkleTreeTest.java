package io.midasprotocol.core.capsule.utils;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.Sha256Hash;
import io.midasprotocol.core.capsule.utils.MerkleTree.Leaf;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class MerkleTreeTest {

	private static List<Sha256Hash> getHash(int hashNum) {
		List<Sha256Hash> hashList = new ArrayList<>();
		for (int i = 0; i < hashNum; i++) {
			byte[] bytes = new byte[4];
			bytes[3] = (byte) (i & 0xFF);
			bytes[2] = (byte) ((i >> 8) & 0xFF);
			bytes[1] = (byte) ((i >> 16) & 0xFF);
			bytes[0] = (byte) ((i >> 24) & 0xFF);
			hashList.add(Sha256Hash.of(bytes));
		}
		return hashList;
	}

	private static Sha256Hash computeHash(Sha256Hash leftHash, Sha256Hash rightHash) {
		return Sha256Hash.of(leftHash.getByteString().concat(rightHash.getByteString()).toByteArray());
	}

	//number: the number of hash
	private static void pareTree(Leaf head, List<Sha256Hash> hashList, int maxRank, int curBank,
								 int number) {
		Leaf left = head.getLeft();
		Leaf right = head.getRight();
		if (curBank < maxRank) {
			curBank++;
			number = number << 1;
			pareTree(left, hashList, maxRank, curBank, number);
			number++;
			if ((number << (maxRank - curBank)) >= hashList
				.size()) {    //The smallest leaf child number = number<<(maxRank-curBank)
				Assert.assertNull(right);
				Assert.assertEquals(head.getHash(), left.getHash());  //No right, leaf = left
			} else {
				pareTree(right, hashList, maxRank, curBank, number);
				Assert.assertEquals(head.getHash(),
					computeHash(left.getHash(), right.getHash())); //hash = sha256(left || right)
			}
		} else {
			// last rank, no child, it is real leaf. Its hash in hashList.
			Assert.assertNull(left);
			Assert.assertNull(right);
			Assert.assertEquals(head.getHash(), hashList.get(number));
			System.out.println("curBank :" + curBank + " number :" + number);
			System.out.println(ByteArray.toHexString(head.getHash().getBytes()));
		}
	}

	private static int getRank(int num) {
		if (num <= 0) {
			return 0;
		}
		if (num == 1) {
			return 1;
		}
		int rank = 0;
		int temp = num;
		while (num > 0) {
			num = num >> 1;
			rank++;
		}
		if (temp == Math.pow(2, rank - 1)) {
			rank -= 1;
		}
		return rank;
	}

	/**
	 * Make a merkletree with no hash.
	 * Will throw a exception.
	 */
	@Test
	public void test0HashNum() {
		List<Sha256Hash> hashList = getHash(0);  //Empty list.
		try {
			MerkleTree.getInstance().createTree(hashList);
			Assert.fail();
		} catch (Exception e) {
			Assert.assertTrue(e instanceof IndexOutOfBoundsException);
		}
	}

	/**
	 * Make a merkletree with 1 hash.
	 * root
	 * /    \
	 * H1   null
	 * /   \
	 * null null
	 */
	@Test
	public void test1HashNum() {
		List<Sha256Hash> hashList = getHash(1);
		MerkleTree tree = MerkleTree.getInstance().createTree(hashList);
		Leaf root = tree.getRoot();
		Assert.assertEquals(root.getHash(), hashList.get(0));

		Leaf left = root.getLeft();
		Assert.assertEquals(left.getHash(), hashList.get(0));
		Assert.assertNull(left.getLeft());
		Assert.assertNull(left.getRight());

		Assert.assertNull(root.getRight());
	}

	/**
	 * Make a merkletree with 2 hash.
	 * root
	 * /     \
	 * H1       H2
	 * /   \    /   \
	 * null null null null
	 */
	@Test
	public void test2HashNum() {
		List<Sha256Hash> hashList = getHash(2);
		MerkleTree tree = MerkleTree.getInstance().createTree(hashList);
		Leaf root = tree.getRoot();
		Assert.assertEquals(root.getHash(), computeHash(hashList.get(0), hashList.get(1)));

		Leaf left = root.getLeft();
		Assert.assertEquals(left.getHash(), hashList.get(0));
		Assert.assertNull(left.getLeft());
		Assert.assertNull(left.getRight());

		Leaf right = root.getRight();
		Assert.assertEquals(right.getHash(), hashList.get(1));
		Assert.assertNull(right.getLeft());
		Assert.assertNull(right.getRight());
	}

	/**
	 * Make a merkletree with any num hash.
	 * <p>
	 * rank0                                 root
	 * rank1                  0                                    1
	 * rank2          0                1                      2
	 * rank3      0      1        2         3             4
	 * rank4    0  1   2   3    4   5     6     7      8     9
	 * rank5  0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18
	 * <p>
	 * leftNum = 2 * headNum
	 * rightNum = leftNum + 1
	 * curBank < maxRank, there must have left child
	 * if have left child but no right child,  headHash = leftHash
	 * if both have left child and right child, headHash = SHA256(leftHash||rightHash)
	 * curBank = maxRank, no child, it is real leaf. Its hash in hashList.
	 */
	@Test
	public void testAnyHashNum() {
		int maxNum = 128;
		for (int hashNum = 1; hashNum <= maxNum; hashNum++) {
			int maxRank = getRank(hashNum);
			List<Sha256Hash> hashList = getHash(hashNum);
			MerkleTree tree = MerkleTree.getInstance().createTree(hashList);
			Leaf root = tree.getRoot();
			pareTree(root, hashList, maxRank, 0, 0);
		}
	}
}