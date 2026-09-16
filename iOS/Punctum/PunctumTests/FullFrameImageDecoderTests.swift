import XCTest
import ImageIO
import UniformTypeIdentifiers
import UIKit
@testable import Punctum

final class FullFrameImageDecoderTests: XCTestCase {
    private func fixture(orientation: Int = 1) -> Data {
        let context = CGContext(data: nil, width: 400, height: 200, bitsPerComponent: 8,
                                bytesPerRow: 400 * 4, space: CGColorSpaceCreateDeviceRGB(),
                                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        context.setFillColor(UIColor.red.cgColor)
        context.fill(CGRect(x: 0, y: 0, width: 200, height: 200))
        context.setFillColor(UIColor.blue.cgColor)
        context.fill(CGRect(x: 200, y: 0, width: 200, height: 200))
        let data = NSMutableData()
        let destination = CGImageDestinationCreateWithData(data, UTType.jpeg.identifier as CFString, 1, nil)!
        CGImageDestinationAddImage(destination, context.makeImage()!, [kCGImagePropertyOrientation: orientation] as CFDictionary)
        XCTAssertTrue(CGImageDestinationFinalize(destination))
        return data as Data
    }

    func testBackgroundDecodePreservesFullFrameAndPixelLimit() async throws {
        let data = fixture()
        let image = await Task.detached { FullFrameImageDecoder.downsample(data, maxPixel: 100) }.value
        let decoded = try XCTUnwrap(image?.cgImage)
        XCTAssertEqual(decoded.width, 100)
        XCTAssertEqual(decoded.height, 50)
        // Sample both halves: an aspect-fill center crop would lose the original proportions.
        let context = CGContext(data: nil, width: 100, height: 50, bitsPerComponent: 8,
                                bytesPerRow: 400, space: CGColorSpaceCreateDeviceRGB(),
                                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        context.draw(decoded, in: CGRect(x: 0, y: 0, width: 100, height: 50))
        let pixels = context.data!.assumingMemoryBound(to: UInt8.self)
        XCTAssertGreaterThan(pixels[25 * 400 + 5 * 4], 200)
        XCTAssertGreaterThan(pixels[25 * 400 + 95 * 4 + 2], 200)
    }

    func testEXIFRotationIsAppliedWithoutSquareCropping() async throws {
        let data = fixture(orientation: 6)
        let image = await Task.detached { FullFrameImageDecoder.downsample(data, maxPixel: 100) }.value
        let decoded = try XCTUnwrap(image?.cgImage)
        XCTAssertEqual(decoded.width, 50)
        XCTAssertEqual(decoded.height, 100)
    }

    func testCorruptDataDoesNotFallBackToDeferredFullSizeDecode() {
        XCTAssertNil(FullFrameImageDecoder.downsample(Data([0, 1, 2, 3]), maxPixel: 100))
    }
}
