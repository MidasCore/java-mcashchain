package org.tron.core.db;

import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.tron.core.capsule.VoteChangeCapsule;

@Component
public class VotesStore extends TronStoreWithRevoking<VoteChangeCapsule> {

  @Autowired
  public VotesStore(@Value("votes") String dbName) {
    super(dbName);
  }

  @Override
  public VoteChangeCapsule get(byte[] key) {
    byte[] value = revokingDB.getUnchecked(key);
    return ArrayUtils.isEmpty(value) ? null : new VoteChangeCapsule(value);
  }
}