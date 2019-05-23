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

import lombok.extern.slf4j.Slf4j;
import io.midasprotocol.common.utils.Sha256Hash;

import java.util.Arrays;

@Slf4j(topic = "capsule")
public class CodeCapsule implements ProtoCapsule<byte[]> {

	private byte[] code;

	public CodeCapsule(byte[] code) {
		this.code = code;
	}

	public Sha256Hash getCodeHash() {
		return Sha256Hash.of(this.code);
	}

	@Override
	public byte[] getData() {
		return this.code;
	}

	@Override
	public byte[] getInstance() {
		return this.code;
	}

	@Override
	public String toString() {
		return Arrays.toString(this.code);
	}
}
