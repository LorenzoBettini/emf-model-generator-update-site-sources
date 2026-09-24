import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

public final class UpdateCompositeMetadata {
	private static final String REPOSITORY_ELEMENT = "repository";

	private UpdateCompositeMetadata() {
	}

	public static void main(String[] arguments) throws Exception {
		if (arguments.length < 2) {
			throw new IllegalArgumentException(
					"Usage: UpdateCompositeMetadata <version> <composite-xml>...");
		}

		String version = arguments[0];
		long timestamp = System.currentTimeMillis();
		List<CompositeMetadata> metadataFiles = new ArrayList<>();
		for (int i = 1; i < arguments.length; i++) {
			metadataFiles.add(CompositeMetadata.read(Path.of(arguments[i]), version));
		}

		for (CompositeMetadata metadata : metadataFiles) {
			metadata.update(version, timestamp);
		}
	}

	private static final class CompositeMetadata {
		private final Path path;
		private final String originalContent;
		private final Document document;
		private final Element timestampProperty;
		private final Element children;
		private final long oldTimestamp;
		private final int oldSize;
		private final boolean containsVersion;

		private CompositeMetadata(Path path, String originalContent, Document document,
				Element timestampProperty, Element children, long oldTimestamp, int oldSize,
				boolean containsVersion) {
			this.path = path;
			this.originalContent = originalContent;
			this.document = document;
			this.timestampProperty = timestampProperty;
			this.children = children;
			this.oldTimestamp = oldTimestamp;
			this.oldSize = oldSize;
			this.containsVersion = containsVersion;
		}

		private static CompositeMetadata read(Path path, String version) throws Exception {
			String content = Files.readString(path, StandardCharsets.UTF_8);
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);

			Document document = factory.newDocumentBuilder()
					.parse(new InputSource(new StringReader(content)));
			Element repository = document.getDocumentElement();
			if (!REPOSITORY_ELEMENT.equals(repository.getTagName())) {
				throw invalid(path, "expected a <repository> document element");
			}

			Element properties = uniqueDirectChild(path, repository, "properties");
			Element timestampProperty = null;
			for (Element property : directChildren(properties, "property")) {
				if ("p2.timestamp".equals(property.getAttribute("name"))) {
					if (timestampProperty != null) {
						throw invalid(path, "contains more than one p2.timestamp property");
					}
					timestampProperty = property;
				}
			}
			if (timestampProperty == null) {
				throw invalid(path, "does not contain a p2.timestamp property");
			}

			Element children = uniqueDirectChild(path, repository, "children");
			boolean containsVersion = directChildren(children, "child").stream()
					.anyMatch(child -> version.equals(child.getAttribute("location")));

			long oldTimestamp = parseLong(path, "p2.timestamp", timestampProperty.getAttribute("value"));
			int oldSize = parseInt(path, "children size", children.getAttribute("size"));
			return new CompositeMetadata(path, content, document, timestampProperty, children,
					oldTimestamp, oldSize, containsVersion);
		}

		private void update(String version, long timestamp) throws Exception {
			if (containsVersion) {
				System.out.println(path + " already contains " + version + "; leaving it unchanged");
				return;
			}

			long incrementedTimestamp = Math.addExact(oldTimestamp, 1);
			timestampProperty.setAttribute("value", Long.toString(Math.max(timestamp, incrementedTimestamp)));
			children.setAttribute("size", Integer.toString(Math.addExact(oldSize, 1)));

			Element child = document.createElement("child");
			child.setAttribute("location", version);
			Node insertionPoint = trailingWhitespace(children);
			children.insertBefore(document.createTextNode(childIndentation(children)), insertionPoint);
			children.insertBefore(child, insertionPoint);

			writeAtomically(path, serializeRepository());
			System.out.println("Added composite repository child " + version + " to " + path);
		}

		private String serializeRepository() throws Exception {
			int repositoryStart = originalContent.indexOf("<" + REPOSITORY_ELEMENT);
			String closingTag = "</" + REPOSITORY_ELEMENT + ">";
			int repositoryEnd = originalContent.lastIndexOf(closingTag);
			if (repositoryStart < 0 || repositoryEnd < repositoryStart) {
				throw invalid(path, "cannot locate the repository element in the source text");
			}
			repositoryEnd += closingTag.length();

			TransformerFactory factory = TransformerFactory.newInstance();
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
			Transformer transformer = factory.newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
			transformer.setOutputProperty(OutputKeys.INDENT, "no");

			StringWriter serialized = new StringWriter();
			transformer.transform(new DOMSource(document.getDocumentElement()), new StreamResult(serialized));
			return originalContent.substring(0, repositoryStart)
					+ serialized
					+ originalContent.substring(repositoryEnd);
		}
	}

	private static List<Element> directChildren(Element parent, String name) {
		List<Element> result = new ArrayList<>();
		for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node instanceof Element element && name.equals(element.getTagName())) {
				result.add(element);
			}
		}
		return result;
	}

	private static Element uniqueDirectChild(Path path, Element parent, String name) {
		List<Element> children = directChildren(parent, name);
		if (children.size() != 1) {
			throw invalid(path, "expected exactly one direct <" + name + "> child of <"
					+ parent.getTagName() + ">");
		}
		return children.getFirst();
	}

	private static Node trailingWhitespace(Element element) {
		Node lastChild = element.getLastChild();
		if (lastChild != null && lastChild.getNodeType() == Node.TEXT_NODE
				&& lastChild.getTextContent().isBlank()) {
			return lastChild;
		}
		return null;
	}

	private static String childIndentation(Element children) {
		for (Node node = children.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node.getNodeType() == Node.TEXT_NODE && node.getTextContent().contains("\n")) {
				String whitespace = node.getTextContent();
				return whitespace.substring(whitespace.lastIndexOf('\n'));
			}
		}
		return System.lineSeparator() + "    ";
	}

	private static long parseLong(Path path, String description, String value) {
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException exception) {
			throw invalid(path, description + " is not a valid integer: " + value);
		}
	}

	private static int parseInt(Path path, String description, String value) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException exception) {
			throw invalid(path, description + " is not a valid integer: " + value);
		}
	}

	private static IllegalArgumentException invalid(Path path, String message) {
		return new IllegalArgumentException("Invalid composite metadata " + path + ": " + message);
	}

	private static void writeAtomically(Path path, String content) throws Exception {
		Path parent = path.toAbsolutePath().getParent();
		Path temporary = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
		try {
			Files.writeString(temporary, content, StandardCharsets.UTF_8);
			try {
				Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
				Files.setPosixFilePermissions(temporary, permissions);
			} catch (UnsupportedOperationException ignored) {
				// Non-POSIX filesystems retain their platform defaults.
			}
			try {
				Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException exception) {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}
}
