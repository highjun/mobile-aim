package dev.sangjun.mobileaim.logging

interface ICaptureLogWriter {
    /** Appends one row to the session's capture log, writing the header first if the file is new. */
    fun append(row: CaptureRow)
}
