package io.midasprotocol.core.db;

import com.google.common.collect.Streams;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.ByteUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import io.midasprotocol.core.capsule.AssetIssueCapsule;

import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static io.midasprotocol.core.config.Parameter.DatabaseConstants.ASSET_ISSUE_COUNT_LIMIT_MAX;

@Slf4j(topic = "DB")
@Component
public class AssetIssueStore extends TronStoreWithRevoking<AssetIssueCapsule> {

	@Autowired
	protected AssetIssueStore(@Value("asset-issue") String dbName) {
		super(dbName);
	}


	@Override
	public AssetIssueCapsule get(byte[] key) {
		return super.getUnchecked(key);
	}

	public byte[] createDbKey(long id) {
		return ByteArray.fromLong(id);
	}

	public AssetIssueCapsule get(long id) {
		byte[] key = createDbKey(id);
		return get(key);
	}

	public void delete(long id) {
		byte[] key = createDbKey(id);
		delete(key);
	}

	public boolean has(long id) {
		byte[] key = createDbKey(id);
		return has(key);
	}


	/**
	 * get all asset issues.
	 */
	public List<AssetIssueCapsule> getAllAssetIssues() {
		return Streams.stream(iterator())
				.map(Entry::getValue)
				.collect(Collectors.toList());
	}

	private List<AssetIssueCapsule> getAssetIssuesPaginated(List<AssetIssueCapsule> assetIssueList,
															long offset, long limit) {
		if (limit < 0 || offset < 0) {
			return null;
		}

//    return Streams.stream(iterator())
//        .map(Entry::getValue)
//        .sorted(Comparator.comparing(a -> a.getName().toStringUtf8(), String::compareTo))
//        .skip(offset)
//        .limit(Math.min(limit, ASSET_ISSUE_COUNT_LIMIT_MAX))
//        .collect(Collectors.toList());

		if (assetIssueList.size() <= offset) {
			return null;
		}
		assetIssueList.sort((o1, o2) -> {
			if (o1.getName() != o2.getName()) {
				return o1.getName().toStringUtf8().compareTo(o2.getName().toStringUtf8());
			}
			return Long.compare(o1.getOrder(), o2.getOrder());
		});
		limit = limit > ASSET_ISSUE_COUNT_LIMIT_MAX ? ASSET_ISSUE_COUNT_LIMIT_MAX : limit;
		long end = offset + limit;
		end = end > assetIssueList.size() ? assetIssueList.size() : end;
		return assetIssueList.subList((int) offset, (int) end);
	}

	public List<AssetIssueCapsule> getAssetIssuesPaginated(long offset, long limit) {
		return getAssetIssuesPaginated(getAllAssetIssues(), offset, limit);
	}
}
