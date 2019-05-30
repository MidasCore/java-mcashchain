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

package io.midasprotocol.core.config.args;

import io.midasprotocol.common.utils.StringUtil;
import io.midasprotocol.core.Wallet;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;

public class Witness implements Serializable {

	private static final long serialVersionUID = -7446501098542377380L;

	@Getter
	private byte[] address;

	@Getter
	private byte[] ownerAddress;

	@Getter
	private String url;

	@Getter
	@Setter
	private long voteCount;

	/**
	 * set address.
	 */
	public void setAddress(final byte[] address) {
		if (!Wallet.addressValid(address)) {
			throw new IllegalArgumentException(
					"The address " + StringUtil.createReadableString(address) + " must be a 20 bytes.");
		}
		this.address = address;
	}

	/**
	 * set ownerAddress
	 */
	public void setOwnerAddress(final byte[] ownerAddress) {
		if (!Wallet.addressValid(ownerAddress)) {
			throw new IllegalArgumentException(
					"The address " + StringUtil.createReadableString(ownerAddress) + " must be a 20 bytes.");
		}
		this.ownerAddress = ownerAddress;
	}

	/**
	 * set url.
	 */
	public void setUrl(final String url) {
		if (StringUtils.isBlank(url)) {
			throw new IllegalArgumentException("The url " + url + " format error.");
		}

		this.url = url;
	}
}
