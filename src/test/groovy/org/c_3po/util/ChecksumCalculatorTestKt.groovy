package org.c_3po.util

import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Paths

import static org.c_3po.util.ChecksumCalculatorKt.sha1String

class ChecksumCalculatorTestKt extends Specification {

    @Unroll
    def "test that .computeSha1Hash computes the correct sha-1 hash of a given file"() {
        expect:
        sha1String(Paths.get(filePath)) == expectedSha1Hash

        where:
        filePath | expectedSha1Hash
        "src/test/resources/test-project-src/css/main.scss" | "e6ce2eaf06d4aa5c64169a225282a19f55ced190"
        "src/test/resources/test-project-src/css/vendor/normalize.scss" | "9056e884fdbde2c1e837f421c7c971cf8be09ba9"
    }
}
