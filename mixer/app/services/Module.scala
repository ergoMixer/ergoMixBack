package services
import config.MainConfigs
import com.google.inject.AbstractModule
import services.admin.{AdminHooks, AdminHooksImpl}
import services.mixer.{ErgoMixHooks, ErgoMixHooksImpl}
import services.main.{MainHooks, MainHooksImpl}

/**
 * This class is a Guice module that tells Guice how to bind several
 * different types. This Guice module is created when the Play
 * application starts.
 *
 * Play will automatically use any class called `Module` that is in
 * the root package. You can create modules in other locations by
 * adding `play.modules.enabled` settings to the `application.conf`
 * configuration file.
 */
class Module extends AbstractModule {

  override def configure(): Unit = {
    // We bind the implementation to the interface (trait) as an eager singleton,
    // which means it is bound immediately when the application starts.
    bind(classOf[MainHooks]).to(classOf[MainHooksImpl]).asEagerSingleton()

    if (MainConfigs.isAdmin)
      bind(classOf[AdminHooks]).to(classOf[AdminHooksImpl]).asEagerSingleton()
    else {
      bind(classOf[ErgoMixHooks]).to(classOf[ErgoMixHooksImpl]).asEagerSingleton()
    }
  }
}
