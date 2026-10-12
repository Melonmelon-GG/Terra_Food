package com.dayan.food.cache;
import com.dayan.food.mapper.DiscoveryVersionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class DiscoveryVersion {
 private final DiscoveryVersionMapper mapper;
 public DiscoveryVersion(DiscoveryVersionMapper mapper){this.mapper=mapper;}
 // A fresh committed read is essential even when a caller holds a repeatable-read snapshot.
 @Transactional(propagation=Propagation.REQUIRES_NEW,readOnly=true,isolation=Isolation.READ_COMMITTED)
 public long current(){return mapper.current();}
 @Transactional(propagation=Propagation.MANDATORY)
 public void advance(){
  // Acquire the shared version row last, after business locks, to avoid lock-order cycles.
  // beforeCommit still uses this transaction and rolls back together with its writes.
  var synchronizations=org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations();
  if(synchronizations.stream().anyMatch(VersionCommit.class::isInstance))return;
  org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new VersionCommit());
 }
 private final class VersionCommit implements org.springframework.transaction.support.TransactionSynchronization {
  @Override public void beforeCommit(boolean readOnly){if(!readOnly&&mapper.advance()!=1)throw new IllegalStateException("Missing discovery version");}
 }
}
