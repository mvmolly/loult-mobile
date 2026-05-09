package family.loult

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import family.loult.app.coil.loultImageLoader
import family.loult.app.di.audioModule
import family.loult.app.di.networkModule
import family.loult.app.di.platformModule
import family.loult.app.di.repositoryModule
import family.loult.app.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class LoultApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@LoultApp)
            modules(
                platformModule(),
                networkModule,
                audioModule,
                repositoryModule,
                viewModelModule,
            )
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = loultImageLoader(context)
}
