import Foundation

enum PunctumFormatting {
    static func detailDate(_ date: Date?) -> String? {
        guard let date else { return nil }
        return detailDateFormatter.string(from: date)
    }

    static func timeSpan(oldest: Date?, newest: Date?) -> String {
        guard let oldest, let newest else { return "" }
        let calendar = Calendar.current
        let first = calendar.dateComponents([.year, .month], from: oldest)
        let last = calendar.dateComponents([.year, .month], from: newest)
        guard let y1 = first.year, let m1 = first.month, let y2 = last.year, let m2 = last.month else { return "" }
        if y1 == y2, m1 == m2 { return "\(y1)年\(m1)月" }
        if y1 == y2 { return "\(y1)年\(m1)-\(m2)月" }
        return "\(y1)年\(m1)月 - \(y2)年\(m2)月"
    }

    static func reversalFilmSpan(_ span: String) -> String {
        let compact = compactSpan(span)
        guard !compact.isEmpty else { return "时间未知" }
        let pattern = #"^(\d{4})\.(\d{2})-(?:(\d{4})\.)?(\d{2})$"#
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: compact, range: NSRange(compact.startIndex..., in: compact))
        else {
            return compact
        }
        func group(_ index: Int) -> String {
            let range = match.range(at: index)
            guard range.location != NSNotFound, let swiftRange = Range(range, in: compact) else { return "" }
            return String(compact[swiftRange])
        }
        let startYear = group(1)
        let startMonth = group(2)
        let endYear = group(3).isEmpty ? startYear : group(3)
        let endMonth = group(4)
        return "\(startYear).\(startMonth) — \(endYear).\(endMonth)"
    }

    static func detailDate(fromExif raw: String) -> String? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = trimmed.count > 16 ? "yyyy:MM:dd HH:mm:ss" : "yyyy:MM:dd HH:mm"
        guard let date = formatter.date(from: trimmed) else { return nil }
        return detailDate(date)
    }

    static func compactSpan(_ span: String) -> String {
        var result = span.replacingOccurrences(of: "年", with: ".")
            .replacingOccurrences(of: "月", with: "")
            .replacingOccurrences(of: " ", with: "")
        result = padSingleDigits(in: result, after: ".")
        result = padSingleDigits(in: result, after: "-")
        return expandSameYearRange(result)
    }

    static func exposure(seconds: Double?) -> String? {
        guard let seconds, seconds > 0 else { return nil }
        if seconds >= 1 {
            let rounded = seconds.rounded()
            return rounded == seconds ? "\(Int(seconds)) s" : String(format: "%.1f s", seconds)
        }
        return "1/\(Int((1 / seconds).rounded())) s"
    }

    static func aperture(_ value: Double?) -> String? {
        guard let value, value > 0 else { return nil }
        return value.rounded() == value ? "F\(Int(value))" : String(format: "F%.1f", value)
    }

    static func focalLength(_ value: Double?) -> String? {
        guard let value, value > 0 else { return nil }
        return value.rounded() == value ? "\(Int(value)) mm" : String(format: "%.1f mm", value)
    }

    static func fileSize(_ bytes: Int) -> String? {
        guard bytes > 0 else { return nil }
        if bytes >= 1_073_741_824 { return String(format: "%.2f GB", Double(bytes) / 1_073_741_824) }
        if bytes >= 1_048_576 { return String(format: "%.1f MB", Double(bytes) / 1_048_576) }
        if bytes >= 1_024 { return String(format: "%.0f KB", Double(bytes) / 1_024) }
        return "\(bytes) B"
    }

    private static func expandSameYearRange(_ compact: String) -> String {
        let pattern = #"^(\d{4})\.(\d{2})-(\d{2})$"#
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: compact, range: NSRange(compact.startIndex..., in: compact)),
              let yearRange = Range(match.range(at: 1), in: compact),
              let startMonthRange = Range(match.range(at: 2), in: compact),
              let endMonthRange = Range(match.range(at: 3), in: compact)
        else {
            return compact
        }
        let year = String(compact[yearRange])
        return "\(year).\(compact[startMonthRange])-\(year).\(compact[endMonthRange])"
    }

    private static func padSingleDigits(in text: String, after marker: Character) -> String {
        var output = ""
        let chars = Array(text)
        for index in chars.indices {
            output.append(chars[index])
            guard chars[index] == marker, index + 1 < chars.count else { continue }
            let next = chars[index + 1]
            let followingIsDigit = index + 2 < chars.count && chars[index + 2].isNumber
            if next.isNumber, !followingIsDigit { output.append("0") }
        }
        return output
    }

    private static let detailDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy.MM.dd HH:mm"
        return formatter
    }()
}
