package helpers

import java.awt.TrayIcon.MessageType
import java.awt._
import java.awt.event.ActionEvent
import java.io.File
import java.net.URI

import app.Configs.readKey
import play.api.Logger

object TrayUtils {
  private val logger: Logger = Logger(this.getClass)

  var triedPrepare = false
  var shownNotification = false
  var trayIcon: TrayIcon = _

  def prepareTray(): Unit = {
    if (triedPrepare) return
    triedPrepare = true
    try {
      val image: Image = Toolkit.getDefaultToolkit.createImage(getClass.getResource("/ergoMixer.png"))
      trayIcon = new TrayIcon(image, "ErgoMixer")
      trayIcon.setImageAutoSize(true)
      trayIcon.setToolTip("ErgoMixer")
      val tray: SystemTray = SystemTray.getSystemTray
      tray.add(trayIcon)
      val popup = new PopupMenu()
      val exitItem = new MenuItem("Exit")
      val openBrowser = new MenuItem("Open ErgoMixer in browser")
      val openLogs = new MenuItem("Open log folder")
      val openConfigFile = new MenuItem("Open configuration file")
      openBrowser.addActionListener((_: ActionEvent) => {
        java.awt.Desktop.getDesktop.browse(new URI(s"http://localhost:${readKey("http.port")}"))
      })
      exitItem.addActionListener((_: ActionEvent) => {
        showNotification("Shutdown", "Please wait, may take a few seconds for ErgoMixer to peacefully shutdown...")
        System.exit(0)
      })
      openLogs.addActionListener((_: ActionEvent) => {
        java.awt.Desktop.getDesktop.open(new File(System.getProperty("user.home") + "/ergoMixer/"))
      })
      var configFile = new File(getClass.getResource("/application.conf").getPath)
      val custom = System.getProperty("config.file", null)
      if (custom != null) configFile = new File(custom)
      openConfigFile.addActionListener((_: ActionEvent) => {
        if (!configFile.exists()) showNotification("Config File", "No config file to open! If you are using jar then provide the default config file first following README.")
        else java.awt.Desktop.getDesktop.open(configFile)
      })
      popup.add(openBrowser)
      popup.add(openConfigFile)
      popup.add(openLogs)
      popup.add(exitItem)
      trayIcon.setPopupMenu(popup)

    } catch {
      case e: Throwable => logger.error(s"failed to create tray icon ${e.getMessage}")
    }
  }

  def showNotification(title: String, message: String): Unit = {
    try {
      prepareTray()
      trayIcon.displayMessage(title, message, MessageType.INFO)
    } catch {
      case _: Throwable =>
    }
  }
}
