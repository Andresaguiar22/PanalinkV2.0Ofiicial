package com.example
import com.example.data.model.ContactWithProfileEntity
import com.example.data.model.EmbeddedProfileAdapter
import com.example.data.model.ProfileSurrogateAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Test
class MoshiSerializationTest {
    // Moshi local: SupabaseClient.moshi no se puede cargar en JVM puro porque
    // el init de SupabaseClient usa android.util.Log.
    private val moshi: Moshi = Moshi.Builder()
        .add(EmbeddedProfileAdapter())
        .add(ProfileSurrogateAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun testMoshi() {
        val adapter = moshi.adapter(ContactWithProfileEntity::class.java)
        val json = """
        {
          "id": "1",
          "owner_user_id": "a",
          "contact_user_id": "b",
          "profiles": {
            "id": "b",
            "display_name": "Test",
            "avatar_url": null,
            "is_profile_complete": true
          }
        }
        """
        val entity = adapter.fromJson(json)
        println("SERIALIZED_ENTITY: " + entity)
        val profile = entity?.getProfile(moshi)
        println("PROFILE: " + profile)
    }
}
