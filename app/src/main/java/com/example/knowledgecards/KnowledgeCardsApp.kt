package com.example.knowledgecards

import android.app.Application
import com.example.knowledgecards.data.AppDatabase
import com.example.knowledgecards.data.CardRepository
import com.example.knowledgecards.data.RoomCardRepository
import com.example.knowledgecards.domain.ProgressStore

/** Manual DI container: keeps singletons for the whole process. */
class AppContainer(context: Application) {
    private val database = AppDatabase.getInstance(context)
    val cardRepository: CardRepository = RoomCardRepository(database.cardDao())
    val progressStore = ProgressStore(context)
}

class KnowledgeCardsApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
