/*
 * java-tron is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * java-tron is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.midasprotocol.core.capsule;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.protos.Contract.AssetIssueContract;
import io.midasprotocol.protos.Contract.AssetIssueContract.FrozenSupply;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j(topic = "capsule")
public class AssetIssueCapsule implements ProtoCapsule<AssetIssueContract> {

	private AssetIssueContract assetIssueContract;

	/**
	 * get asset issue contract from bytes data.
	 */
	public AssetIssueCapsule(byte[] data) {
		try {
			this.assetIssueContract = AssetIssueContract.parseFrom(data);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage());
		}
	}

	public AssetIssueCapsule(AssetIssueContract assetIssueContract) {
		this.assetIssueContract = assetIssueContract;
	}

	public byte[] getData() {
		return this.assetIssueContract.toByteArray();
	}

	@Override
	public AssetIssueContract getInstance() {
		return this.assetIssueContract;
	}

	@Override
	public String toString() {
		return this.assetIssueContract.toString();
	}

	public ByteString getName() {
		return this.assetIssueContract.getName();
	}

	public long getId() {
		return this.assetIssueContract.getId();
	}

	public void setId(long id) {
		this.assetIssueContract = this.assetIssueContract.toBuilder()
			.setId(id)
			.build();
	}

	public int getPrecision() {
		return this.assetIssueContract.getPrecision();
	}

	public void setPrecision(int precision) {
		this.assetIssueContract = this.assetIssueContract.toBuilder()
			.setPrecision(precision)
			.build();
	}

	public long getOrder() {
		return this.assetIssueContract.getOrder();
	}

	public void setOrder(long order) {
		this.assetIssueContract = this.assetIssueContract.toBuilder()
			.setOrder(order)
			.build();
	}

	public byte[] createDbKey() {
		return ByteArray.fromLong(getId());
	}

	public int getNum() {
		return this.assetIssueContract.getNum();
	}

	public int getMcashNum() {
		return this.assetIssueContract.getMcashNum();
	}

	public long getStartTime() {
		return this.assetIssueContract.getStartTime();
	}

	public long getEndTime() {
		return this.assetIssueContract.getEndTime();
	}

	public ByteString getOwnerAddress() {
		return this.assetIssueContract.getOwnerAddress();
	}

	public int getFrozenSupplyCount() {
		return getInstance().getFrozenSupplyCount();
	}

	public List<FrozenSupply> getFrozenSupplyList() {
		return getInstance().getFrozenSupplyList();
	}

	public long getFrozenSupply() {
		List<FrozenSupply> frozenList = getFrozenSupplyList();
		final long[] frozenBalance = {0};
		frozenList.forEach(frozen -> frozenBalance[0] = Long.sum(frozenBalance[0],
			frozen.getFrozenAmount()));
		return frozenBalance[0];
	}

	public long getFreeAssetBandwidthLimit() {
		return this.assetIssueContract.getFreeAssetBandwidthLimit();
	}

	public void setFreeAssetBandwidthLimit(long newLimit) {
		this.assetIssueContract = this.assetIssueContract.toBuilder()
			.setFreeAssetBandwidthLimit(newLimit).build();
	}

	public long getPublicFreeAssetBandwidthLimit() {
		return this.assetIssueContract.getPublicFreeAssetBandwidthLimit();
	}

	public void setPublicFreeAssetBandwidthLimit(long newPublicLimit) {
		this.assetIssueContract = this.assetIssueContract.toBuilder()
			.setPublicFreeAssetBandwidthLimit(newPublicLimit).build();
	}

	public long getPublicFreeAssetBandwidthUsage() {
		return this.assetIssueContract.getPublicFreeAssetBandwidthUsage();
	}

	public void setPublicFreeAssetBandwidthUsage(long value) {
		this.assetIssueContract = this.assetIssueContract.toBuilder()
			.setPublicFreeAssetBandwidthUsage(value).build();
	}

	public long getPublicLatestFreeBandwidthTime() {
		return this.assetIssueContract.getPublicLatestFreeBandwidthTime();
	}

	public void setPublicLatestFreeBandwidthTime(long time) {
		this.assetIssueContract = this.assetIssueContract.toBuilder()
			.setPublicLatestFreeBandwidthTime(time).build();
	}

	public void setUrl(ByteString newUrl) {
		this.assetIssueContract = this.assetIssueContract.toBuilder()
			.setUrl(newUrl).build();
	}

	public void setDescription(ByteString description) {
		this.assetIssueContract = this.assetIssueContract.toBuilder()
			.setDescription(description).build();
	}
}
