import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch
import uuid

spec=importlib.util.spec_from_file_location('memory_deletion',Path(__file__).parents[1]/'memory_deletion.py')
worker=importlib.util.module_from_spec(spec);spec.loader.exec_module(worker)

class MemoryDeletion(unittest.TestCase):
    def test_delete_requires_empty_strong_read_and_stable_subject(self):
        subject=str(uuid.uuid4())
        with patch.object(worker,'milvus_request',side_effect=[{'code':0},{'code':0,'data':[]}]) as request:
            worker.delete_subject('http://fixture','memory',subject)
        self.assertEqual(request.call_args_list[1].args[2]['consistencyLevel'],'Strong')
        self.assertEqual(request.call_args_list[0].args[2]['filter'],'subject_id == "'+subject+'"')
        with patch.object(worker,'milvus_request') as request:
            with self.assertRaises(ValueError):worker.delete_subject('http://fixture','memory','x" or true')
            request.assert_not_called()

    def test_remaining_records_are_not_reported_deleted(self):
        with patch.object(worker,'milvus_request',side_effect=[{'code':0},{'code':0,'data':[{'subject_id':'remaining'}]}]):
            with self.assertRaisesRegex(RuntimeError,'EXTERNAL_RECORDS_REMAIN'):
                worker.delete_subject('http://fixture','memory',str(uuid.uuid4()))

    def test_failed_external_delete_requeues_owned_lease_and_does_not_complete(self):
        queries=[];subject=str(uuid.uuid4())
        def sql(_,query):
            queries.append(query)
            if query.startswith('SELECT id,subject_id'):return '5\t'+subject
            if query.startswith('SELECT COUNT'):return '0'
            return ''
        with patch.object(worker,'sql',side_effect=sql),patch.object(worker,'require_stopped_writers'),patch.object(worker,'delete_subject',side_effect=TimeoutError):
            self.assertEqual(worker.drain('fixture','fixture','http://fixture','memory',1),{'completed':0,'failed':1})
        self.assertFalse(any("SET status='DONE'" in query for query in queries))
        self.assertIn("next_attempt_at=DATE_ADD(NOW(),INTERVAL 5 MINUTE)",queries[-1])
        self.assertIn("worker_owner=",queries[-1])

    def test_live_account_cannot_be_deleted_by_stale_outbox(self):
        subject=str(uuid.uuid4())
        def sql(_,query):
            if query.startswith('SELECT id,subject_id'):return '5\t'+subject
            if query.startswith('SELECT COUNT'):return '1'
            return ''
        with patch.object(worker,'sql',side_effect=sql),patch.object(worker,'require_stopped_writers'),patch.object(worker,'delete_subject') as delete:
            self.assertEqual(worker.drain('fixture','fixture','http://fixture','memory',1)['failed'],1)
            delete.assert_not_called()

if __name__=='__main__':unittest.main()
