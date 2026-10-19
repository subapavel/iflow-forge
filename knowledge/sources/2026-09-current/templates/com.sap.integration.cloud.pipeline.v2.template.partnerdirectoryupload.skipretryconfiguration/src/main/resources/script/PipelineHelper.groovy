package src.main.resources.script
import groovy.json.JsonSlurper
import java.util.Base64

class PipelineHelper {
	static def decodeIfBase64(String input) {
		def output = input

		if (input instanceof String && PipelineHelper.isBase64(input)) {
			try {
				def decoded = new String(Base64.decoder.decode(input.trim()), "UTF-8")
				try {
					def jsonParsed = new JsonSlurper().parseText(decoded)
					if (jsonParsed instanceof Map) {
						output = jsonParsed
					} else {
						output = decoded
					}
				} catch (Exception ignored) {
					output = decoded // Not JSON, but still decoded
				}
			} catch (Exception ignored) {
				output = input // Fall back to original
			}
		}

		return output
	}

	static boolean isBase64(String s) {
		return s?.matches("[A-Za-z0-9+/=\\s]+")
	}

	static boolean isValidKeyValueString(String input) {
		if (!input?.trim()) return false

		def pairs = input.split(/\|/)
		for (pair in pairs) {
			if (!pair.contains(":")) return false

			def kv = pair.split(":", 2)
			if (kv.size() != 2) return false

			def key = kv[0]?.trim()
			def value = kv[1]?.trim()

			if (!key || !value) return false
			if (key.contains(":") || key.contains("|") || value.contains(":") || value.contains("|")) return false
		}
		return true
	}
	
	static void dumpKeyValueAsTEXT(String title, Map<String, Object> map, StringBuilder sb) {
		sb.append(title).append('\n')
		map.each { key, value ->
			sb.append(String.format(" %-40s: %-40s\n", key, value))
		}
		sb.append('\n')
	}
}