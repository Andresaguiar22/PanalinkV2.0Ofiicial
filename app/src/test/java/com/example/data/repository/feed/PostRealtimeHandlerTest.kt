package com.example.data.repository.feed

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.PanalinkDatabase
import com.example.data.database.PostEntity
import com.example.data.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PostRealtimeHandlerTest {

    private lateinit var db: PanalinkDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var handler: PostRealtimeHandler

    @Before
    fun setup() {
        PostRealtimeHandler.resetForTest()
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PanalinkDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        handler = PostRealtimeHandler.getInstanceForTest(db.postDao(), scope)
    }

    @After
    fun teardown() {
        PostRealtimeHandler.resetForTest()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        db.close()
    }

    @Test
    fun test1_InsertPostEvent() = runBlocking {
        val record = JSONObject().apply {
            put("id", "post-insert-1")
            put("user_id", "user-1")
            put("content", "Hello Realtime Post")
            put("likes_count", 0)
            put("comments_count", 0)
            put("created_at", "2026-09-05T00:00:00Z")
        }
        SupabaseClient.emitRealtimePost(
            SupabaseClient.PostRealtimeUpdate("INSERT", "post-insert-1", record)
        )
        kotlinx.coroutines.delay(50)

        val post = db.postDao().getPostById("post-insert-1")
        assertNotNull(post)
        assertEquals("Hello Realtime Post", post?.content)
    }

    @Test
    fun test2_UpdatePostEvent() = runBlocking {
        db.postDao().upsert(
            PostEntity(
                id = "post-update-1",
                authorId = "user-1",
                type = "TEXT",
                content = "Old content",
                mediaUrlsJson = "[]",
                audioUrl = null,
                privacy = "PUBLIC",
                likesCount = 1,
                commentsCount = 0,
                currentUserLiked = false,
                createdAt = "2026-09-05T00:00:00Z",
                updatedAt = "2026-09-05T00:00:00Z"
            )
        )

        val record = JSONObject().apply {
            put("id", "post-update-1")
            put("user_id", "user-1")
            put("content", "Updated content via realtime")
            put("likes_count", 2)
            put("comments_count", 0)
            put("created_at", "2026-09-05T00:00:00Z")
            put("updated_at", "2026-09-05T01:00:00Z")
        }
        SupabaseClient.emitRealtimePost(
            SupabaseClient.PostRealtimeUpdate("UPDATE", "post-update-1", record)
        )
        kotlinx.coroutines.delay(50)

        val post = db.postDao().getPostById("post-update-1")
        assertNotNull(post)
        assertEquals("Updated content via realtime", post?.content)
        assertEquals(2, post?.likesCount)
    }

    @Test
    fun test3_DeletePostEvent() = runBlocking {
        db.postDao().upsert(
            PostEntity(
                id = "post-delete-1",
                authorId = "user-1",
                type = "TEXT",
                content = "To be deleted",
                mediaUrlsJson = "[]",
                audioUrl = null,
                privacy = "PUBLIC",
                likesCount = 0,
                commentsCount = 0,
                currentUserLiked = false,
                createdAt = "2026-09-05T00:00:00Z"
            )
        )

        assertNotNull(db.postDao().getPostById("post-delete-1"))

        SupabaseClient.emitRealtimePostDeletion("post-delete-1")
        kotlinx.coroutines.delay(50)

        assertNull(db.postDao().getPostById("post-delete-1"))
    }

    @Test
    fun test4_DuplicateEventIdempotent() = runBlocking {
        val record = JSONObject().apply {
            put("id", "post-dup-1")
            put("user_id", "user-1")
            put("content", "Idempotent content")
            put("likes_count", 5)
            put("comments_count", 0)
            put("created_at", "2026-09-05T00:00:00Z")
            put("updated_at", "2026-09-05T00:00:00Z")
        }
        val update1 = SupabaseClient.PostRealtimeUpdate("UPDATE", "post-dup-1", record)
        val update2 = SupabaseClient.PostRealtimeUpdate("UPDATE", "post-dup-1", record)
        SupabaseClient.emitRealtimePost(update1)
        SupabaseClient.emitRealtimePost(update2)
        kotlinx.coroutines.delay(50)

        val posts = db.postDao().getAllPostsFlow().first()
        assertEquals(1, posts.size)
        assertEquals("Idempotent content", posts[0].content)
    }

    @Test
    fun test5_PreserveCurrentUserLiked() = runBlocking {
        db.postDao().upsert(
            PostEntity(
                id = "post-like-1",
                authorId = "user-1",
                type = "TEXT",
                content = "Liked post",
                mediaUrlsJson = "[]",
                audioUrl = null,
                privacy = "PUBLIC",
                likesCount = 10,
                commentsCount = 0,
                currentUserLiked = true,
                createdAt = "2026-09-05T00:00:00Z",
                updatedAt = "2026-09-05T00:00:00Z"
            )
        )

        val record = JSONObject().apply {
            put("id", "post-like-1")
            put("user_id", "user-1")
            put("content", "Liked post updated")
            put("likes_count", 10)
            put("comments_count", 0)
            put("created_at", "2026-09-05T00:00:00Z")
            put("updated_at", "2026-09-05T01:00:00Z")
        }
        SupabaseClient.emitRealtimePost(
            SupabaseClient.PostRealtimeUpdate("UPDATE", "post-like-1", record)
        )
        kotlinx.coroutines.delay(50)

        val post = db.postDao().getPostById("post-like-1")
        assertNotNull(post)
        assertEquals(true, post?.currentUserLiked)
    }

    @Test
    fun test6_PreserveOptimisticComments() = runBlocking {
        db.postDao().upsert(
            PostEntity(
                id = "post-comment-1",
                authorId = "user-1",
                type = "TEXT",
                content = "Comment post",
                mediaUrlsJson = "[]",
                audioUrl = null,
                privacy = "PUBLIC",
                likesCount = 0,
                commentsCount = 3,
                currentUserLiked = false,
                createdAt = "2026-09-05T00:00:00Z",
                updatedAt = "2026-09-05T00:00:00Z"
            )
        )
        db.pendingSocialActionDao().insertAction(
            com.example.data.database.PendingSocialActionEntity(
                localActionId = "pending-comment-1",
                userId = "user-1",
                targetId = "post-comment-1",
                actionType = "COMMENT",
                payload = "{\"text\":\"pending\"}",
                isReel = false
            )
        )

        val record = JSONObject().apply {
            put("id", "post-comment-1")
            put("user_id", "user-1")
            put("content", "Comment post updated")
            put("likes_count", 0)
            put("comments_count", 2)
            put("created_at", "2026-09-05T00:00:00Z")
            put("updated_at", "2026-09-05T01:00:00Z")
        }
        SupabaseClient.emitRealtimePost(
            SupabaseClient.PostRealtimeUpdate("UPDATE", "post-comment-1", record)
        )
        kotlinx.coroutines.delay(50)

        val post = db.postDao().getPostById("post-comment-1")
        assertNotNull(post)
        assertEquals(3, post?.commentsCount)
    }

    @Test
    fun test10_RemoteLowerNoPending_Wins() = runBlocking {
        db.postDao().upsert(
            PostEntity(
                id = "post-count-drop",
                authorId = "user-1",
                type = "TEXT",
                content = "Count drop",
                mediaUrlsJson = "[]",
                audioUrl = null,
                privacy = "PUBLIC",
                likesCount = 0,
                commentsCount = 10,
                currentUserLiked = false,
                createdAt = "2026-09-05T00:00:00Z",
                updatedAt = "2026-09-05T00:00:00Z"
            )
        )

        val record = JSONObject().apply {
            put("id", "post-count-drop")
            put("user_id", "user-1")
            put("content", "Count drop updated")
            put("likes_count", 0)
            put("comments_count", 8)
            put("created_at", "2026-09-05T00:00:00Z")
            put("updated_at", "2026-09-05T01:00:00Z")
        }
        SupabaseClient.emitRealtimePost(
            SupabaseClient.PostRealtimeUpdate("UPDATE", "post-count-drop", record)
        )
        kotlinx.coroutines.delay(50)

        val post = db.postDao().getPostById("post-count-drop")
        assertNotNull(post)
        assertEquals(8, post?.commentsCount)
    }

    @Test
    fun test11_RemoteLowerWithPendingComment_PreservesLocal() = runBlocking {
        db.postDao().upsert(
            PostEntity(
                id = "post-count-pending",
                authorId = "user-1",
                type = "TEXT",
                content = "Count pending",
                mediaUrlsJson = "[]",
                audioUrl = null,
                privacy = "PUBLIC",
                likesCount = 0,
                commentsCount = 10,
                currentUserLiked = false,
                createdAt = "2026-09-05T00:00:00Z",
                updatedAt = "2026-09-05T00:00:00Z"
            )
        )
        db.pendingSocialActionDao().insertAction(
            com.example.data.database.PendingSocialActionEntity(
                localActionId = "pending-comment-2",
                userId = "user-1",
                targetId = "post-count-pending",
                actionType = "COMMENT",
                payload = "{\"text\":\"pending\"}",
                isReel = false
            )
        )

        val record = JSONObject().apply {
            put("id", "post-count-pending")
            put("user_id", "user-1")
            put("content", "Count pending updated")
            put("likes_count", 0)
            put("comments_count", 8)
            put("created_at", "2026-09-05T00:00:00Z")
            put("updated_at", "2026-09-05T01:00:00Z")
        }
        SupabaseClient.emitRealtimePost(
            SupabaseClient.PostRealtimeUpdate("UPDATE", "post-count-pending", record)
        )
        kotlinx.coroutines.delay(50)

        val post = db.postDao().getPostById("post-count-pending")
        assertNotNull(post)
        assertEquals(10, post?.commentsCount)
    }

    @Test
    fun test12_RemoteHigher_Wins() = runBlocking {
        db.postDao().upsert(
            PostEntity(
                id = "post-count-up",
                authorId = "user-1",
                type = "TEXT",
                content = "Count up",
                mediaUrlsJson = "[]",
                audioUrl = null,
                privacy = "PUBLIC",
                likesCount = 0,
                commentsCount = 8,
                currentUserLiked = false,
                createdAt = "2026-09-05T00:00:00Z",
                updatedAt = "2026-09-05T01:00:00Z"
            )
        )

        val record = JSONObject().apply {
            put("id", "post-count-up")
            put("user_id", "user-1")
            put("content", "Count up updated")
            put("likes_count", 0)
            put("comments_count", 10)
            put("created_at", "2026-09-05T00:00:00Z")
            put("updated_at", "2026-09-05T01:00:00Z")
        }
        SupabaseClient.emitRealtimePost(
            SupabaseClient.PostRealtimeUpdate("UPDATE", "post-count-up", record)
        )
        kotlinx.coroutines.delay(50)

        val post = db.postDao().getPostById("post-count-up")
        assertNotNull(post)
        assertEquals(10, post?.commentsCount)
    }

    @Test
    fun test13_ReplayOfSameEventDoesNotCorruptCount() = runBlocking {
        db.postDao().upsert(
            PostEntity(
                id = "post-count-replay",
                authorId = "user-1",
                type = "TEXT",
                content = "Count replay",
                mediaUrlsJson = "[]",
                audioUrl = null,
                privacy = "PUBLIC",
                likesCount = 0,
                commentsCount = 9,
                currentUserLiked = false,
                createdAt = "2026-09-05T00:00:00Z",
                updatedAt = "2026-09-05T01:00:00Z"
            )
        )

        val record = JSONObject().apply {
            put("id", "post-count-replay")
            put("user_id", "user-1")
            put("content", "Count replay updated")
            put("likes_count", 0)
            put("comments_count", 7)
            put("created_at", "2026-09-05T00:00:00Z")
            put("updated_at", "2026-09-05T01:00:00Z")
        }
        val update1 = SupabaseClient.PostRealtimeUpdate("UPDATE", "post-count-replay", record)
        val update2 = SupabaseClient.PostRealtimeUpdate("UPDATE", "post-count-replay", record)
        SupabaseClient.emitRealtimePost(update1)
        SupabaseClient.emitRealtimePost(update2)
        kotlinx.coroutines.delay(50)

        val post = db.postDao().getPostById("post-count-replay")
        assertNotNull(post)
        assertEquals(7, post?.commentsCount)
        val posts = db.postDao().getAllPostsFlow().first()
        assertEquals(1, posts.size)
    }

    @Test
    fun test14_LoweredCommentsDoesNotAlterCurrentUserLiked() = runBlocking {
        db.postDao().upsert(
            PostEntity(
                id = "post-like-comment-mixed",
                authorId = "user-1",
                type = "TEXT",
                content = "Like preserved, comments refreshed",
                mediaUrlsJson = "[]",
                audioUrl = null,
                privacy = "PUBLIC",
                likesCount = 5,
                commentsCount = 10,
                currentUserLiked = true,
                createdAt = "2026-09-05T00:00:00Z",
                updatedAt = "2026-09-05T00:00:00Z"
            )
        )

        val record = JSONObject().apply {
            put("id", "post-like-comment-mixed")
            put("user_id", "user-1")
            put("content", "Like preserved, comments refreshed")
            put("likes_count", 5)
            put("comments_count", 8)
            put("created_at", "2026-09-05T00:00:00Z")
            put("updated_at", "2026-09-05T01:00:00Z")
        }
        SupabaseClient.emitRealtimePost(
            SupabaseClient.PostRealtimeUpdate("UPDATE", "post-like-comment-mixed", record)
        )
        kotlinx.coroutines.delay(50)

        val post = db.postDao().getPostById("post-like-comment-mixed")
        assertNotNull(post)
        assertEquals(8, post?.commentsCount)
        assertEquals(true, post?.currentUserLiked)
    }

    @Test
    fun test7_MalformedPayloadNoCrash() = runBlocking {
        val malformedRecord = JSONObject().apply {
            put("id", "post-malformed")
            put("media_urls", "not-an-array-string")
        }
        SupabaseClient.emitRealtimePost(
            SupabaseClient.PostRealtimeUpdate("INSERT", "post-malformed", malformedRecord)
        )
        kotlinx.coroutines.delay(50)

        val post = db.postDao().getPostById("post-malformed")
        assertNotNull(post)
        assertEquals("[]", post?.mediaUrlsJson)
    }

    @Test
    fun test8_UnknownEventNeverFallsIntoMessageParser() = runBlocking {
        val record = JSONObject().apply {
            put("id", "unknown-record")
            put("some_field", "value")
        }
        SupabaseClient.emitRealtimePost(
            SupabaseClient.PostRealtimeUpdate("UNKNOWN_EVENT", "unknown-record", record)
        )
        kotlinx.coroutines.delay(50)

        val messages = db.messageDao().getMessagesForChat("any-chat")
        assertEquals(0, messages.size)
        assertNull(db.postDao().getPostById("unknown-record"))
    }

    @Test
    fun test9_SameTimestampDifferentPayloadIsNotDeduplicated() = runBlocking {
        val first = JSONObject().apply {
            put("id", "post-same-ts")
            put("user_id", "user-1")
            put("content", "First update")
            put("updated_at", "2026-09-05T01:00:00Z")
        }
        val second = JSONObject().apply {
            put("id", "post-same-ts")
            put("user_id", "user-1")
            put("content", "Second legitimate update")
            put("updated_at", "2026-09-05T01:00:00Z")
        }
        SupabaseClient.emitRealtimePost(
            SupabaseClient.PostRealtimeUpdate("UPDATE", "post-same-ts", first)
        )
        SupabaseClient.emitRealtimePost(
            SupabaseClient.PostRealtimeUpdate("UPDATE", "post-same-ts", second)
        )
        kotlinx.coroutines.delay(50)

        val post = db.postDao().getPostById("post-same-ts")
        assertNotNull(post)
        assertEquals("Second legitimate update", post?.content)
    }
}
