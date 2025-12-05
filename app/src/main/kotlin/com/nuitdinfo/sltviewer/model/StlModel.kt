package com.nuitdinfo.sltviewer.model

import com.nuitdinfo.sltviewer.model.ArrayModel
import com.nuitdinfo.sltviewer.util.Util
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.regex.Pattern
import kotlin.math.abs

class StlModel(inputStream: InputStream) : ArrayModel() {
    init {
        val stream = BufferedInputStream(inputStream, INPUT_BUFFER_SIZE)
        stream.mark(ASCII_TEST_SIZE)
        val isText = isTextFormat(stream)
        stream.reset()
        if (isText) {
            readText(stream)
        } else {
            readBinary(stream)
        }

        if (vertexCount <= 0 || vertexBuffer == null || normalBuffer == null) {
            throw IOException("Invalid model.")
        }
    }

    public override fun initModelMatrix(boundSize: Float) {
        val zRotation = 60f
        val xRotation = -90.0f
        initModelMatrix(boundSize, xRotation, 0.0f, zRotation)
        var scale = getBoundScale(boundSize)
        if (scale == 0.0f) {
            scale = 1.0f
        }
        floorOffset = (minZ - centerMassZ) / scale
    }

    private fun isTextFormat(stream: InputStream): Boolean {
        val testBytes = ByteArray(ASCII_TEST_SIZE)
        val bytesRead = stream.read(testBytes, 0, testBytes.size)
        val string = String(testBytes, 0, bytesRead)
        return string.contains("solid") && string.contains("facet") && string.contains("vertex")
    }

    private fun readText(stream: InputStream) {
        val normals = mutableListOf<Float>()
        val vertices = mutableListOf<Float>()
        BufferedReader(InputStreamReader(stream, Charsets.UTF_8), INPUT_BUFFER_SIZE).use { reader ->
            var line: String?
            val wsRegex = "\\s+".toRegex()
            var centerMassX = 0.0
            var centerMassY = 0.0
            var centerMassZ = 0.0
            var totalVolume = 0.0
            val facetNormalRegex = "facet normal ".toRegex()
            val vertexRegex = "vertex ".toRegex()
            val currentVertices = mutableListOf<Float>()

            while (reader.readLine().also { line = it } != null) {
                val trimmed = line!!.trim()
                if (trimmed.isEmpty()) continue

                if (trimmed.startsWith("facet")) {
                    currentVertices.clear()
                    val normalLine = trimmed.replaceFirst(facetNormalRegex, "").trim()
                    val parts = wsRegex.split(normalLine)
                    if (parts.size >= 3) {
                        val x = parts[0].toFloat()
                        val y = parts[1].toFloat()
                        val z = parts[2].toFloat()
                        // duplicate normal for three vertices
                        repeat(3) {
                            normals.add(x); normals.add(y); normals.add(z)
                        }
                    }
                } else if (trimmed.startsWith("vertex")) {
                    val vertLine = trimmed.replaceFirst(vertexRegex, "").trim()
                    val parts = wsRegex.split(vertLine)
                    if (parts.size >= 3) {
                        val x = parts[0].toFloat()
                        val y = parts[1].toFloat()
                        val z = parts[2].toFloat()
                        adjustMaxMin(x, y, z)
                        vertices.add(x); vertices.add(y); vertices.add(z)
                        currentVertices.add(x); currentVertices.add(y); currentVertices.add(z)
                        centerMassX += x.toDouble()
                        centerMassY += y.toDouble()
                        centerMassZ += z.toDouble()
                    }
                } else if (trimmed.startsWith("endfacet")) {
                    if (currentVertices.size == 9) {
                        totalVolume += signedVolumeOfTriangle(
                            currentVertices[0], currentVertices[1], currentVertices[2],
                            currentVertices[3], currentVertices[4], currentVertices[5],
                            currentVertices[6], currentVertices[7], currentVertices[8]
                        )
                    }
                }
            }

            vertexCount = if (vertices.size >= 3) vertices.size / 3 else 0
            if (vertexCount > 0) {
                this.centerMassX = (centerMassX / vertexCount).toFloat()
                this.centerMassY = (centerMassY / vertexCount).toFloat()
                this.centerMassZ = (centerMassZ / vertexCount).toFloat()
            } else {
                this.centerMassX = 0f; this.centerMassY = 0f; this.centerMassZ = 0f
            }
            volume = abs(totalVolume.toFloat())

            var vbb = ByteBuffer.allocateDirect(vertices.size * BYTES_PER_FLOAT)
            vbb.order(ByteOrder.nativeOrder())
            vertexBuffer = vbb.asFloatBuffer()
            for (i in vertices.indices) vertexBuffer!!.put(vertices[i])
            vertexBuffer!!.position(0)

            vbb = ByteBuffer.allocateDirect(normals.size * BYTES_PER_FLOAT)
            vbb.order(ByteOrder.nativeOrder())
            normalBuffer = vbb.asFloatBuffer()
            for (i in normals.indices) normalBuffer!!.put(normals[i])
            normalBuffer!!.position(0)
        }
    }


    private fun readBinary(inputStream: BufferedInputStream) {
        val chunkSize = 50
        val tempBytes = ByteArray(chunkSize)
        inputStream.skip(HEADER_SIZE.toLong())
        inputStream.read(tempBytes, 0, BYTES_PER_FLOAT)

        val vectorSize: Int = Util.readIntLe(tempBytes, 0)
        vertexCount = vectorSize * 3
        if (vertexCount < 0 || vertexCount > 10000000) {
            throw IOException("Invalid model.")
        }

        var centerMassX = 0.0
        var centerMassY = 0.0
        var centerMassZ = 0.0
        var totalVolume = 0.0
        val vertexArray = FloatArray(vertexCount * COORDS_PER_VERTEX)
        val normalArray = FloatArray(vertexCount * COORDS_PER_VERTEX)
        var x: Float
        var y: Float
        var z: Float
        var x1: Float
        var y1: Float
        var z1: Float
        var x2: Float
        var y2: Float
        var z2: Float
        var x3: Float
        var y3: Float
        var z3: Float
        var vertexPtr = 0
        var normalPtr = 0
        var haveNormals = false
        for (i in 0 until vectorSize) {
            inputStream.read(tempBytes, 0, tempBytes.size)
            x = java.lang.Float.intBitsToFloat(Util.readIntLe(tempBytes, 0))
            y = java.lang.Float.intBitsToFloat(Util.readIntLe(tempBytes, 4))
            z = java.lang.Float.intBitsToFloat(Util.readIntLe(tempBytes, 8))
            normalArray[normalPtr++] = x
            normalArray[normalPtr++] = y
            normalArray[normalPtr++] = z
            normalArray[normalPtr++] = x
            normalArray[normalPtr++] = y
            normalArray[normalPtr++] = z
            normalArray[normalPtr++] = x
            normalArray[normalPtr++] = y
            normalArray[normalPtr++] = z
            if (!haveNormals) {
                if (x != 0.0f || y != 0.0f || z != 0.0f) {
                    haveNormals = true
                }
            }
            x1 = java.lang.Float.intBitsToFloat(Util.readIntLe(tempBytes, 12))
            y1 = java.lang.Float.intBitsToFloat(Util.readIntLe(tempBytes, 16))
            z1 = java.lang.Float.intBitsToFloat(Util.readIntLe(tempBytes, 20))
            adjustMaxMin(x1, y1, z1)
            centerMassX += x1.toDouble()
            centerMassY += y1.toDouble()
            centerMassZ += z1.toDouble()
            vertexArray[vertexPtr++] = x1
            vertexArray[vertexPtr++] = y1
            vertexArray[vertexPtr++] = z1

            x2 = java.lang.Float.intBitsToFloat(Util.readIntLe(tempBytes, 24))
            y2 = java.lang.Float.intBitsToFloat(Util.readIntLe(tempBytes, 28))
            z2 = java.lang.Float.intBitsToFloat(Util.readIntLe(tempBytes, 32))
            adjustMaxMin(x2, y2, z2)
            centerMassX += x2.toDouble()
            centerMassY += y2.toDouble()
            centerMassZ += z2.toDouble()
            vertexArray[vertexPtr++] = x2
            vertexArray[vertexPtr++] = y2
            vertexArray[vertexPtr++] = z2

            x3 = java.lang.Float.intBitsToFloat(Util.readIntLe(tempBytes, 36))
            y3 = java.lang.Float.intBitsToFloat(Util.readIntLe(tempBytes, 40))
            z3 = java.lang.Float.intBitsToFloat(Util.readIntLe(tempBytes, 44))
            adjustMaxMin(x3, y3, z3)
            centerMassX += x3.toDouble()
            centerMassY += y3.toDouble()
            centerMassZ += z3.toDouble()
            vertexArray[vertexPtr++] = x3
            vertexArray[vertexPtr++] = y3
            vertexArray[vertexPtr++] = z3

            totalVolume += signedVolumeOfTriangle(x1, y1, z1, x2, y2, z2, x3, y3, z3)
        }
        this.centerMassX = (centerMassX / vertexCount).toFloat()
        this.centerMassY = (centerMassY / vertexCount).toFloat()
        this.centerMassZ = (centerMassZ / vertexCount).toFloat()
        volume = abs(totalVolume.toFloat())

        if (!haveNormals) {
            val customNormal = FloatArray(3)
            var i = 0
            while (i < vertexCount) {
                Util.calculateNormal(
                    vertexArray[i * 3],
                    vertexArray[i * 3 + 1],
                    vertexArray[i * 3 + 2],
                    vertexArray[(i + 1) * 3],
                    vertexArray[(i + 1) * 3 + 1],
                    vertexArray[(i + 1) * 3 + 2],
                    vertexArray[(i + 2) * 3],
                    vertexArray[(i + 2) * 3 + 1],
                    vertexArray[(i + 2) * 3 + 2],
                    customNormal
                )
                normalArray[i * 3] = customNormal[0]
                normalArray[i * 3 + 1] = customNormal[1]
                normalArray[i * 3 + 2] = customNormal[2]
                normalArray[(i + 1) * 3] = customNormal[0]
                normalArray[(i + 1) * 3 + 1] = customNormal[1]
                normalArray[(i + 1) * 3 + 2] = customNormal[2]
                normalArray[(i + 2) * 3] = customNormal[0]
                normalArray[(i + 2) * 3 + 1] = customNormal[1]
                normalArray[(i + 2) * 3 + 2] = customNormal[2]
                i += 3
            }
        }

        var vbb = ByteBuffer.allocateDirect(vertexArray.size * BYTES_PER_FLOAT)
        vbb.order(ByteOrder.nativeOrder())
        vertexBuffer = vbb.asFloatBuffer()
        vertexBuffer!!.put(vertexArray)
        vertexBuffer!!.position(0)

        vbb = ByteBuffer.allocateDirect(normalArray.size * BYTES_PER_FLOAT)
        vbb.order(ByteOrder.nativeOrder())
        normalBuffer = vbb.asFloatBuffer()
        normalBuffer!!.put(normalArray)
        normalBuffer!!.position(0)
    }

    private fun signedVolumeOfTriangle(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, x3: Float, y3: Float, z3: Float): Double {
        return (-x3 * y2 * z1 +
                x2 * y3 * z1 +
                x3 * y1 * z2 -
                x1 * y3 * z2 -
                x2 * y1 * z3 +
                x1 * y2 * z3) / 6.0
    }

    companion object {
        private const val HEADER_SIZE = 80
        private const val ASCII_TEST_SIZE = 256
    }
}