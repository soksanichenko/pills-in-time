package app.zelgray.pills_in_time.di

import app.zelgray.pills_in_time.util.NowProvider
import app.zelgray.pills_in_time.util.SystemNowProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UtilModule {

    @Binds
    @Singleton
    abstract fun bindNowProvider(impl: SystemNowProvider): NowProvider
}
