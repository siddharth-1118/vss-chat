package com.example.chat.data.repository

import android.content.Context
import android.provider.ContactsContract
import com.example.chat.data.local.dao.ContactDao
import com.example.chat.data.local.entity.ContactEntity
import com.google.i18n.phonenumbers.PhoneNumberUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contactDao: ContactDao,
    private val supabaseClient: SupabaseClient
) {
    private val phoneUtil = PhoneNumberUtil.getInstance()

    fun getContacts(): Flow<List<ContactEntity>> = contactDao.getAllContacts()

    suspend fun syncContacts() = withContext(Dispatchers.IO) {
        val rawContacts = fetchLocalContacts()
        val normalizedContacts = rawContacts.mapNotNull { (name, rawPhone) ->
            normalizePhone(rawPhone)?.let { it to name }
        }.toMap()

        val phones = normalizedContacts.keys.toList()
        
        // Chunking by 100 for Supabase RPC/Query
        val appUsers = mutableSetOf<String>()
        phones.chunked(100).forEach { batch ->
            try {
                val response = supabaseClient.postgrest["profiles"]
                    .select {
                        filter {
                            isIn("phone", batch)
                        }
                    }.decodeList<ProfileDto>()
                
                appUsers.addAll(response.map { it.phone })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val contactEntities = normalizedContacts.map { (phone, name) ->
            ContactEntity(
                phone = phone,
                displayName = name,
                avatarUrl = null, // Can be fetched from ProfileDto if needed
                isAppUser = appUsers.contains(phone)
            )
        }

        contactDao.insertContacts(contactEntities)
    }

    private fun fetchLocalContacts(): List<Pair<String, String>> {
        val contacts = mutableListOf<Pair<String, String>>()
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            null
        )

        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = it.getString(nameIndex) ?: "Unknown"
                val number = it.getString(numberIndex) ?: continue
                contacts.add(name to number)
            }
        }
        return contacts
    }

    private fun normalizePhone(rawPhone: String): String? {
        return try {
            val numberProto = phoneUtil.parse(rawPhone, "US") // Default to US or detect from locale
            if (phoneUtil.isValidNumber(numberProto)) {
                phoneUtil.format(numberProto, PhoneNumberUtil.PhoneNumberFormat.E164)
            } else null
        } catch (e: Exception) {
            null
        }
    }
}

@kotlinx.serialization.Serializable
data class ProfileDto(
    val id: String,
    val phone: String,
    val display_name: String?,
    val avatar_url: String?
)
