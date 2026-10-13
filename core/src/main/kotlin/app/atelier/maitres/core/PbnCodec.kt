package app.atelier.maitres.core

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Format binaire compact pour mettre en cache une toile découpée et la progression.
 *
 * Les étiquettes sont compressées par plages (run-length) : une toile de 1200 px
 * passe de ~3,6 Mo d'entiers bruts à quelques centaines de Ko.
 */
object PbnCodec {
    private const val CANVAS_MAGIC = 0x50424E31 // "PBN1"
    private const val PROGRESS_MAGIC = 0x50524731 // "PRG1"

    fun writeCanvas(canvas: PaintByNumber, output: OutputStream) {
        val out = DataOutputStream(output.buffered())
        out.writeInt(CANVAS_MAGIC)
        out.writeInt(canvas.width)
        out.writeInt(canvas.height)
        out.writeIntArray(canvas.palette)
        out.writeIntArray(canvas.regionColor)
        out.writeFloatArray(canvas.labelX)
        out.writeFloatArray(canvas.labelY)
        out.writeFloatArray(canvas.labelRadius)
        val labels = canvas.labels
        var i = 0
        while (i < labels.size) {
            val value = labels[i]
            var run = 1
            while (i + run < labels.size && labels[i + run] == value) run++
            out.writeVarInt(value)
            out.writeVarInt(run)
            i += run
        }
        out.flush()
    }

    fun readCanvas(input: InputStream): PaintByNumber {
        val inp = DataInputStream(input.buffered())
        if (inp.readInt() != CANVAS_MAGIC) throw IOException("format de toile inconnu")
        val width = inp.readInt()
        val height = inp.readInt()
        val palette = inp.readIntArray()
        val regionColor = inp.readIntArray()
        val labelX = inp.readFloatArray()
        val labelY = inp.readFloatArray()
        val labelRadius = inp.readFloatArray()
        val labels = IntArray(width * height)
        var i = 0
        while (i < labels.size) {
            val value = inp.readVarInt()
            val run = inp.readVarInt()
            if (run <= 0 || i + run > labels.size) throw IOException("plage invalide")
            labels.fill(value, i, i + run)
            i += run
        }
        return PaintByNumber(width, height, labels, palette, regionColor, labelX, labelY, labelRadius)
    }

    fun writeProgress(fills: IntArray, output: OutputStream) {
        val out = DataOutputStream(output.buffered())
        out.writeInt(PROGRESS_MAGIC)
        out.writeIntArray(fills)
        out.flush()
    }

    fun readProgress(input: InputStream): IntArray {
        val inp = DataInputStream(input.buffered())
        if (inp.readInt() != PROGRESS_MAGIC) throw IOException("format de progression inconnu")
        return inp.readIntArray()
    }

    private fun DataOutputStream.writeIntArray(values: IntArray) {
        writeInt(values.size)
        for (v in values) writeInt(v)
    }

    private fun DataInputStream.readIntArray(): IntArray = IntArray(readCount()) { readInt() }

    private fun DataOutputStream.writeFloatArray(values: FloatArray) {
        writeInt(values.size)
        for (v in values) writeFloat(v)
    }

    private fun DataInputStream.readFloatArray(): FloatArray = FloatArray(readCount()) { readFloat() }

    private fun DataInputStream.readCount(): Int =
        readInt().also { if (it < 0 || it > 50_000_000) throw IOException("taille invalide: $it") }

    private fun DataOutputStream.writeVarInt(value: Int) {
        var v = value
        while (v and 0x7F.inv() != 0) {
            writeByte((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        writeByte(v)
    }

    private fun DataInputStream.readVarInt(): Int {
        var result = 0
        var shift = 0
        while (shift < 35) {
            val b = readUnsignedByte()
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
        throw IOException("entier variable invalide")
    }
}
