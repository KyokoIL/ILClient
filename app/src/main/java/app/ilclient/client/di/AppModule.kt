package app.ilclient.client.di

import android.content.Context
import androidx.room.Room
import app.ilclient.client.data.local.ILClientDao
import app.ilclient.client.data.local.ILClientDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ILClientDatabase {
        return Room.databaseBuilder(
            context,
            ILClientDatabase::class.java,
            "ilclient_database.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideDao(db: ILClientDatabase): ILClientDao = db.dao()
}