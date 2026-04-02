package buet.com.demo3;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class Database {
    private static final String DB_URL = "jdbc:sqlite:verification.db";
    private static final String DEFAULT_ADMIN_USERNAME = "Arman";
    private static final String DEFAULT_ADMIN_PASSWORD = "@ureli@n";

    private Database() {
    }

    public static void initializeDatabase() {
        createAdminsTable();
        createReferenceDocumentsTable();
        createVerificationRequestsTable();
        createGoogleSessionTable();
        relaxAttachmentConstraints();
        seedDefaultAdmin();
    }

    public static boolean authenticateAdmin(String username, String password) throws SQLException {
        String sql = """
                SELECT password_hash
                FROM admins
                WHERE username = ?
                """;

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && hashText(password).equals(resultSet.getString("password_hash"));
            }
        }
    }

    public static void saveReferenceDocument(
            String username,
            String docType,
            String documentPath,
            String certificatePath,
            String documentHash,
            String certificateHash
    ) throws SQLException {
        String sql = """
                INSERT INTO reference_documents (
                    admin_username,
                    doc_type,
                    document_path,
                    certificate_path,
                    document_hash,
                    certificate_hash
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, docType);
            statement.setString(3, documentPath);
            setNullableString(statement, 4, certificatePath);
            statement.setString(5, documentHash);
            setNullableString(statement, 6, certificateHash);
            statement.executeUpdate();
        }
    }

    public static ReferenceDocument findLatestReferenceDocument(String docType) throws SQLException {
        String sql = """
                SELECT id, admin_username, doc_type, document_path, certificate_path, document_hash, certificate_hash, created_at
                FROM reference_documents
                WHERE doc_type = ?
                ORDER BY created_at DESC, id DESC
                LIMIT 1
                """;

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, docType);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return mapReferenceDocument(resultSet);
            }
        }
    }

    public static List<ReferenceDocument> getAllReferenceDocuments() throws SQLException {
        String sql = """
                SELECT id, admin_username, doc_type, document_path, certificate_path, document_hash, certificate_hash, created_at
                FROM reference_documents
                ORDER BY created_at DESC, id DESC
                """;

        List<ReferenceDocument> documents = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                documents.add(mapReferenceDocument(resultSet));
            }
        }
        return documents;
    }

    public static int countReferenceDocuments() throws SQLException {
        return countQuery("SELECT COUNT(*) FROM reference_documents");
    }

    public static int countVerifiedUsers() throws SQLException {
        return countQuery("SELECT COUNT(DISTINCT email) FROM verification_requests");
    }

    public static void saveGoogleUserSession(String email, String name, String pictureUrl) throws SQLException {
        String sql = """
                INSERT INTO google_user_session (id, email, name, picture_url, updated_at)
                VALUES (1, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(id) DO UPDATE SET
                    email = excluded.email,
                    name = excluded.name,
                    picture_url = excluded.picture_url,
                    updated_at = CURRENT_TIMESTAMP
                """;

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            statement.setString(2, name);
            setNullableString(statement, 3, pictureUrl);
            statement.executeUpdate();
        }
    }

    public static GoogleUserSession getGoogleUserSession() throws SQLException {
        String sql = """
                SELECT email, name, picture_url
                FROM google_user_session
                WHERE id = 1
                """;

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return null;
            }
            return new GoogleUserSession(
                    resultSet.getString("email"),
                    resultSet.getString("name"),
                    resultSet.getString("picture_url")
            );
        }
    }

    public static void clearGoogleUserSession() throws SQLException {
        String sql = "DELETE FROM google_user_session WHERE id = 1";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }

    public static void deleteReferenceDocument(int id) throws SQLException {
        String sql = "DELETE FROM reference_documents WHERE id = ?";
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.executeUpdate();
        }
    }

    public static void saveVerificationRequest(
            String email,
            String docType,
            String documentPath,
            String certificatePath,
            String documentHash,
            String certificateHash,
            String comparisonResult,
            Integer referenceDocumentId
    ) throws SQLException {
        String sql = """
                INSERT INTO verification_requests (
                    email,
                    doc_type,
                    document_path,
                    certificate_path,
                    document_hash,
                    certificate_hash,
                    comparison_result,
                    reference_document_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            statement.setString(2, docType);
            statement.setString(3, documentPath);
            setNullableString(statement, 4, certificatePath);
            statement.setString(5, documentHash);
            setNullableString(statement, 6, certificateHash);
            statement.setString(7, comparisonResult);
            if (referenceDocumentId == null) {
                statement.setNull(8, java.sql.Types.INTEGER);
            } else {
                statement.setInt(8, referenceDocumentId);
            }
            statement.executeUpdate();
        }
    }

    private static int countQuery(String sql) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private static ReferenceDocument mapReferenceDocument(ResultSet resultSet) throws SQLException {
        return new ReferenceDocument(
                resultSet.getInt("id"),
                resultSet.getString("admin_username"),
                resultSet.getString("doc_type"),
                resultSet.getString("document_path"),
                resultSet.getString("certificate_path"),
                resultSet.getString("document_hash"),
                resultSet.getString("certificate_hash"),
                resultSet.getString("created_at")
        );
    }

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    private static void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            statement.setNull(index, java.sql.Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private static void createAdminsTable() {
        executeStatement("""
                CREATE TABLE IF NOT EXISTS admins (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL UNIQUE,
                    password_hash TEXT NOT NULL,
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    private static void createReferenceDocumentsTable() {
        executeStatement("""
                CREATE TABLE IF NOT EXISTS reference_documents (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    admin_username TEXT NOT NULL,
                    doc_type TEXT NOT NULL,
                    document_path TEXT NOT NULL,
                    certificate_path TEXT,
                    document_hash TEXT NOT NULL,
                    certificate_hash TEXT,
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    private static void createVerificationRequestsTable() {
        executeStatement("""
                CREATE TABLE IF NOT EXISTS verification_requests (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    email TEXT NOT NULL,
                    doc_type TEXT NOT NULL,
                    document_path TEXT NOT NULL,
                    certificate_path TEXT,
                    document_hash TEXT NOT NULL,
                    certificate_hash TEXT,
                    comparison_result TEXT NOT NULL,
                    reference_document_id INTEGER,
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    private static void createGoogleSessionTable() {
        executeStatement("""
                CREATE TABLE IF NOT EXISTS google_user_session (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    email TEXT NOT NULL,
                    name TEXT NOT NULL,
                    picture_url TEXT,
                    updated_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    private static void relaxAttachmentConstraints() {
        try {
            if (tableNeedsAttachmentMigration("reference_documents")) {
                try (Connection connection = getConnection()) {
                    migrateReferenceDocumentsTable(connection);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to prepare reference document table migration.", exception);
        }

        try {
            if (tableNeedsAttachmentMigration("verification_requests")) {
                try (Connection connection = getConnection()) {
                    migrateVerificationRequestsTable(connection);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to prepare verification request table migration.", exception);
        }
    }

    private static boolean tableNeedsAttachmentMigration(String tableName) throws SQLException {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement("PRAGMA table_info(" + tableName + ")");
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String columnName = resultSet.getString("name");
                int notNull = resultSet.getInt("notnull");
                if (("certificate_path".equals(columnName) || "certificate_hash".equals(columnName)) && notNull == 1) {
                    return true;
                }
            }
            return false;
        }
    }

    private static void migrateReferenceDocumentsTable(Connection connection) throws SQLException {
        try (PreparedStatement rename = connection.prepareStatement("ALTER TABLE reference_documents RENAME TO reference_documents_old")) {
            rename.execute();
        }
        try (PreparedStatement create = connection.prepareStatement("""
                CREATE TABLE reference_documents (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    admin_username TEXT NOT NULL,
                    doc_type TEXT NOT NULL,
                    document_path TEXT NOT NULL,
                    certificate_path TEXT,
                    document_hash TEXT NOT NULL,
                    certificate_hash TEXT,
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
                """)) {
            create.execute();
        }
        try (PreparedStatement copy = connection.prepareStatement("""
                INSERT INTO reference_documents (
                    id, admin_username, doc_type, document_path, certificate_path, document_hash, certificate_hash, created_at
                )
                SELECT
                    id,
                    admin_username,
                    doc_type,
                    document_path,
                    NULLIF(certificate_path, ''),
                    document_hash,
                    NULLIF(certificate_hash, ''),
                    created_at
                FROM reference_documents_old
                """)) {
            copy.executeUpdate();
        }
        try (PreparedStatement drop = connection.prepareStatement("DROP TABLE reference_documents_old")) {
            drop.execute();
        }
    }

    private static void migrateVerificationRequestsTable(Connection connection) throws SQLException {
        try (PreparedStatement rename = connection.prepareStatement("ALTER TABLE verification_requests RENAME TO verification_requests_old")) {
            rename.execute();
        }
        try (PreparedStatement create = connection.prepareStatement("""
                CREATE TABLE verification_requests (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    email TEXT NOT NULL,
                    doc_type TEXT NOT NULL,
                    document_path TEXT NOT NULL,
                    certificate_path TEXT,
                    document_hash TEXT NOT NULL,
                    certificate_hash TEXT,
                    comparison_result TEXT NOT NULL,
                    reference_document_id INTEGER,
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
                """)) {
            create.execute();
        }
        try (PreparedStatement copy = connection.prepareStatement("""
                INSERT INTO verification_requests (
                    id, email, doc_type, document_path, certificate_path, document_hash, certificate_hash, comparison_result, reference_document_id, created_at
                )
                SELECT
                    id,
                    email,
                    doc_type,
                    document_path,
                    NULLIF(certificate_path, ''),
                    document_hash,
                    NULLIF(certificate_hash, ''),
                    comparison_result,
                    reference_document_id,
                    created_at
                FROM verification_requests_old
                """)) {
            copy.executeUpdate();
        }
        try (PreparedStatement drop = connection.prepareStatement("DROP TABLE verification_requests_old")) {
            drop.execute();
        }
    }

    private static void executeStatement(String sql) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize SQLite database.", exception);
        }
    }

    private static void seedDefaultAdmin() {
        String updateSql = "UPDATE admins SET password_hash = ? WHERE username = ?";
        String insertSql = """
                INSERT INTO admins (username, password_hash)
                SELECT ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM admins WHERE username = ?
                )
                """;

        try (Connection connection = getConnection();
             PreparedStatement updateStatement = connection.prepareStatement(updateSql);
             PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
            updateStatement.setString(1, hashText(DEFAULT_ADMIN_PASSWORD));
            updateStatement.setString(2, DEFAULT_ADMIN_USERNAME);
            updateStatement.executeUpdate();

            insertStatement.setString(1, DEFAULT_ADMIN_USERNAME);
            insertStatement.setString(2, hashText(DEFAULT_ADMIN_PASSWORD));
            insertStatement.setString(3, DEFAULT_ADMIN_USERNAME);
            insertStatement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to seed default admin account.", exception);
        }
    }

    private static String hashText(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : hashed) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    public record ReferenceDocument(
            int id,
            String adminUsername,
            String docType,
            String documentPath,
            String certificatePath,
            String documentHash,
            String certificateHash,
            String createdAt
    ) {
        public String fileName() {
            return Path.of(documentPath).getFileName().toString();
        }
    }

    public record GoogleUserSession(
            String email,
            String name,
            String pictureUrl
    ) {
    }
}
