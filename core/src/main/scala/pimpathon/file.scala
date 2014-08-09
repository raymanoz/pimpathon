package pimpathon

import java.io.File

import pimpathon.any._


object file {
  implicit class RichFile(file: File) {
    def create(): File = file.tap(_.createNewFile)
  }

  def withTempFile[A](f: File => A): A = withTempFile(".tmp", "temp")(f)

  def withTempFile[A](suffix: String, prefix: String = "temp")(f: File => A): A = {
    val file = File.createTempFile(prefix, suffix)

    try f(file) finally file.delete
  }


  def withTempDirectory[A](f: File => A): A = withTempDirectory(".tmp", "temp")(f)

  def withTempDirectory[A](suffix: String, prefix: String = "temp")(f: File => A): A = {
    file.withTempFile[A](suffix, prefix)((file: File) => {
      file.delete()
      file.mkdir()

      f(file)
    })
  }
}
