import asyncio
import unittest
from unittest.mock import AsyncMock, patch
from fastapi import HTTPException
from app import main


class Connection:
    def __init__(self, disconnected=False): self.disconnected=disconnected
    async def is_disconnected(self): return self.disconnected


def request(subject="a"*36):
    return main.ChatRequest(subjectId=subject,serviceContext="x"*64,username="user",displayName="User",message="Hello")


class AgentBudgets(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        main._agent_slots=asyncio.Semaphore(4)
        main._active_subjects=set()
        main._active_subjects_lock=asyncio.Lock()
        main._waiting_count=0

    async def test_duplicate_subject_rejected_without_starting_model(self):
        release=asyncio.Event()
        async def work(_):
            await release.wait()
            return main.ChatResponse(reply="test")
        with patch.object(main,"_run_chat",work):
            active=asyncio.create_task(main.chat(request(),Connection()))
            await asyncio.sleep(.01)
            with self.assertRaises(HTTPException) as error: await main.chat(request(),Connection())
            self.assertEqual(error.exception.status_code,429)
            release.set();await active
        self.assertEqual(main._active_subjects,set())

    async def test_disconnect_cancels_model_and_releases_capacity(self):
        finished=asyncio.Event()
        async def work(_):
            try: await asyncio.Event().wait()
            finally: finished.set()
        with patch.object(main,"_run_chat",work):
            with self.assertRaises(HTTPException) as error: await main.chat(request(),Connection(True))
        self.assertEqual(error.exception.status_code,499)
        self.assertTrue(finished.is_set())
        self.assertEqual(main._agent_slots._value,4)
        self.assertEqual(main._active_subjects,set())

    async def test_full_queue_refuses_before_model(self):
        main._waiting_count=8
        with patch.object(main,"_run_chat",AsyncMock()) as work:
            with self.assertRaises(HTTPException) as error: await main.chat(request(),Connection())
        self.assertEqual(error.exception.status_code,429)
        work.assert_not_called()

    async def test_cancel_queued_request_returns_its_budget(self):
        main._agent_slots=asyncio.Semaphore(0)
        pending=asyncio.create_task(main.chat(request(),Connection()))
        await asyncio.sleep(.01)
        pending.cancel()
        with self.assertRaises(asyncio.CancelledError): await pending
        self.assertEqual(main._waiting_count,0)
        self.assertEqual(main._active_subjects,set())

    async def test_memory_is_explicitly_isolated(self):
        self.assertIn("不会被保存",await main.recall_memories("a"*36,"private text"))
        self.assertFalse(hasattr(main,"remember_exchange"))


if __name__=="__main__": unittest.main()
