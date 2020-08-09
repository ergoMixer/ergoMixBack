package services

import java.awt.TrayIcon.MessageType
import java.awt.event.{ActionEvent, ActionListener}
import java.awt._
import java.io.File
import java.net.URI

import app.Configs.readKey
import mixer.ErgoMixerUtils.getStackTraceStr
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
      openBrowser.addActionListener((_: ActionEvent) => {
        java.awt.Desktop.getDesktop.browse(new URI(s"http://localhost:${readKey("http.port")}"))
      })
      exitItem.addActionListener((_: ActionEvent) => {
        System.exit(0)
      })
      openLogs.addActionListener((_: ActionEvent) => {
        java.awt.Desktop.getDesktop.open(new File(System.getProperty("user.home") + "/ergoMixer/"))
      })
      popup.add(openBrowser)
      popup.add(openLogs)
      popup.add(exitItem)
      trayIcon.setPopupMenu(popup)

    } catch {
      case e: Throwable => logger.error(getStackTraceStr(e))

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
