package didzapp.HF_Translator;

import java.security.SecureRandom;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Function;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Version;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.StaleObjectStateException;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.exception.LockAcquisitionException;
import org.hibernate.query.Query;

import didzapp.LOGGER;
import didzapp.HF_Translator.TranslatorContent.Translatable;
import didzapp.HF_Translator.TranslatorResourcePaths.ToConfigFiles;

/**
 * Represents a translation entity stored in the database. This class maps to a
 * table in the database and handles persistence using Hibernate.
 */
@Entity
public class TranslatorEntity {
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////// Hibernate Mapping
	// Column names used for mapping to database columns
	protected static final String Column_id = "id"; //$NON-NLS-1$
	protected static final String Column_version = "version"; //$NON-NLS-1$
	private static final String Column_StringIN = "StringIN"; //$NON-NLS-1$
	private static final String Column_modleCode = "ModelCode"; //$NON-NLS-1$
	private static final String Column_StringOut = "StringOUT"; //$NON-NLS-1$
	private static final String Column_LastUsed = "LastUsed"; //$NON-NLS-1$
	// Primary key field
	@Id
	@Column(name = Column_id, nullable = false, length = 50, columnDefinition = "VARCHAR(50) NOT NULL COLLATE utf8mb4_bin")
	private String id;
	// Version field for optimistic locking
	@Version
	@Column(name = Column_version, nullable = false)
	private int version;
	// Input string to be translated or looked for
	@Column(name = Column_StringIN, nullable = false, length = 5000, columnDefinition = "VARCHAR(5000) NOT NULL COLLATE utf8mb4_bin")
	private String StringIN;
	// Output string after translation
	@Column(name = Column_modleCode, nullable = false, length = 255, columnDefinition = "VARCHAR(50) NOT NULL COLLATE utf8mb4_bin")
	private String ModelCode;
	// Output string after translation
	@Column(name = Column_StringOut, nullable = false, length = 5000, columnDefinition = "VARCHAR(5000) NOT NULL COLLATE utf8mb4_bin")
	private String StringOUT;
	// Timestamp when the entity was last used
	@Column(name = Column_LastUsed, nullable = false)
	private Timestamp LastUsed;
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////// Instance Methods

	/**
	 * Default constructor required by Hibernate.
	 */
	public TranslatorEntity() {
	}

	/**
	 * Sets the input string value. Accepts either a String or a Translateable.
	 *
	 * @param StringIN The input value to set
	 * @return This entity instance for chaining
	 */
	protected TranslatorEntity setStringIN(final Object StringIN) {
		if (StringIN instanceof String || StringIN instanceof Translatable) {
			this.StringIN = (StringIN instanceof String ? (String) StringIN : StringIN.toString());
			return this;
		}
		return null;
	}

	/**
	 * Sets the model code, this is the local code from the language object. (e.g.,
	 * Language.ENGLISH.getLocale().getLanguage(), "en")
	 *
	 * @param ModelCode The model code to set
	 * @return This entity instance for chaining
	 */
	protected TranslatorEntity setModelCode(final String ModelCode) {
		this.ModelCode = ModelCode;
		return this;
	}

	/**
	 * Sets the output string.
	 *
	 * @param StringOUT The output string to set
	 * @return This entity instance for chaining
	 */
	private TranslatorEntity setTranslation(final String StringOUT) {
		this.StringOUT = StringOUT;
		return this;
	}

	/**
	 * Gets the output string value.
	 *
	 * @return The output string
	 */
	protected String getTranslation() {
		return this.StringOUT;
	}

	/**
	 * Saves this entity to the database. If no id exists, generates one. Otherwise,
	 * updates the existing record.
	 */
	protected void save() {
		if ((this.ModelCode != null) && (this.StringIN != null) && (this.StringOUT != null)) {
			HibernateUtil.createSessionAndExecuteTransactionWithRetry(hibernateSession -> {
				this.LastUsed = Translator.quickTimestamp.timestamp();
				if (this.id == null) {
					this.id = HibernateUtil.generateUniqueID(TranslatorEntity.class);
					hibernateSession.persist(this);
				} else {
					hibernateSession.merge(this);
				}
				LOGGER.log("Entity Saved"); //$NON-NLS-1$
				return null;
			}, 3);
		} else {
			LOGGER.log("Save Failed: Null Vars"); //$NON-NLS-1$
		}
	}

	/**
	 * Deletes this entity from the database.
	 */
	protected void delete() {
		if (this.id != null) {
			HibernateUtil.createSessionAndExecuteTransactionWithRetry(hibernateSession -> {
				hibernateSession.remove(this);
				LOGGER.log("Entity Deleted"); //$NON-NLS-1$
				return null;
			}, 3);
		} else {
			LOGGER.log("Delete Failed: No id / This Entity Was Never Saved"); //$NON-NLS-1$
		}
	}
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////// Static Methods

	/**
	 * Gets a translation by its id.
	 *
	 * @param id The id to search for
	 * @return The entity if found, or null
	 */
	public static TranslatorEntity getTranslation(final String id) {
		HibernateUtil.createSessionAndExecuteTransactionWithRetry(hibernateSession -> {
			final TranslatorEntity entity = hibernateSession.get(TranslatorEntity.class, id);
			LOGGER.log("Entity Found With id: " + id); //$NON-NLS-1$
			return entity;
		}, 3);
		return null;
	}

	/**
	 * Retrieves a translation from the database and deletes duplicates.
	 *
	 * @param modelCode The model code to search for
	 * @param input     The input value to search for
	 * @return The matching entity or null if not found
	 */
	public static TranslatorEntity getTranslation(final String modelCode, final Object input) {
		return HibernateUtil.createSessionAndExecuteTransactionWithRetry(hibernateSession -> {
			if (input != null) {
				final boolean isString = input instanceof String;
				final CriteriaBuilder cb = hibernateSession.getCriteriaBuilder();
				final CriteriaQuery<TranslatorEntity> cq = cb.createQuery(TranslatorEntity.class);
				final Root<TranslatorEntity> root = cq.from(TranslatorEntity.class);
				cq.select(root)
						.where(
								cb.equal(root.get(Column_modleCode), modelCode),
								cb.equal(root.get(Column_StringIN), (isString ? (String) input : input.toString())));
				final List<TranslatorEntity> result = hibernateSession.createQuery(cq).getResultList();
				final List<TranslatorEntity> entitiesToRemove = new ArrayList<>();
				TranslatorEntity lastValidEntity = null;
				// First pass: identify what to keep and what to remove
				for (final TranslatorEntity entity : result) {
					if ((entity.getTranslation() == null) || entity.getTranslation().isEmpty()) {
						entitiesToRemove.add(entity);
					} else {
						if (lastValidEntity != null) {
							entitiesToRemove.add(lastValidEntity);
						}
						lastValidEntity = entity;
					}
				}
				for (final TranslatorEntity entityToRemove : entitiesToRemove) {
					hibernateSession.remove(entityToRemove);
				}
				if (lastValidEntity != null) {
					lastValidEntity.LastUsed = Translator.quickTimestamp.timestamp();
					return lastValidEntity;
				}
			}
			return null;
		}, 3);
	}

	/**
	 * Saves a new translation to the database.
	 *
	 * @param modelCode        The model code
	 * @param input            The input value
	 * @param translatedString The translated output string
	 */
	public static void save(final String modelCode, final Object input, final String translatedString) {
		new TranslatorEntity().setModelCode(modelCode)
				.setStringIN((input instanceof String ? (String) input : input.toString()))
				.setTranslation(translatedString)
				.save();
		LOGGER.log("Translation Added To Database"); //$NON-NLS-1$
	}

	/**
	 * Deletes an entity from the database by its id.
	 *
	 * @param id The id of the entity to delete
	 */
	public static void delete(final String id) {
		HibernateUtil.createSessionAndExecuteTransactionWithRetry(hibernateSession -> {
			final TranslatorEntity entity = hibernateSession.get(TranslatorEntity.class, id);
			if (entity != null) {
				hibernateSession.remove(entity);
				LOGGER.log("Entity Deleted"); //$NON-NLS-1$
			} else {
				LOGGER.log("Delete Failed: No Entity Found"); //$NON-NLS-1$
			}
			return null;
		}, 3);
	}

	/**
	 * Deletes all translations matching the given model code and input.
	 *
	 * @param modelCode The model code
	 * @param input     The input value
	 * @return The result of the operation (currently unused)
	 */
	public static void delete(final String modelCode, final Object input) {
		HibernateUtil.createSessionAndExecuteTransactionWithRetry(hibernateSession -> {
			if (input != null) {
				final boolean isString = input instanceof String;
				final CriteriaBuilder cb = hibernateSession.getCriteriaBuilder();
				final CriteriaQuery<TranslatorEntity> cq = cb.createQuery(TranslatorEntity.class);
				final Root<TranslatorEntity> root = cq.from(TranslatorEntity.class);
				cq.select(root)
						.where(
								cb.equal(root.get(Column_modleCode), modelCode),
								cb.equal(root.get(Column_StringIN), (isString ? (String) input : input.toString())));
				final List<TranslatorEntity> result = hibernateSession.createQuery(cq).getResultList();
				for (final TranslatorEntity t : result) {
					hibernateSession.remove(t);
				}
			}
			return null;
		}, 3);
	}
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////// Static
	/// Utility Methods

	/**
	 * Deletes duplicate translations (keeps only the latest one).
	 */
	public static void deleteDuplicateTranslations() {
		HibernateUtil.createSessionAndExecuteTransactionWithRetry(hibernateSession -> {
			final String hql = "DELETE FROM " + TranslatorEntity.class //$NON-NLS-1$
					.getSimpleName() + " e " + "WHERE e." + Column_id + " NOT IN (" + "   SELECT MAX(e2." + Column_id + ") " + "   FROM " + TranslatorEntity.class //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
							.getSimpleName() + " e2 " + "   GROUP BY e2." + Column_modleCode + ", e2." + Column_StringIN + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			final Query<?> query = hibernateSession.createQuery(hql);
			final int deletedCount = query.executeUpdate();
			LOGGER.log("Deleted Duplicate Translations: " + deletedCount); //$NON-NLS-1$
			return null;
		}, 3);
	}

	/**
	 * Deletes translations that haven't been used in the last 360 days.
	 */
	public static void deleteUnusedTranslations() {
		final Timestamp removeTime = Timestamp.from(Instant.now().minus(360, ChronoUnit.DAYS));
		HibernateUtil.createSessionAndExecuteTransactionWithRetry(hibernateSession -> {
			final String hql = "DELETE FROM " + TranslatorEntity.class //$NON-NLS-1$
					.getSimpleName() + " e WHERE e." + Column_LastUsed + " < :cutoff"; //$NON-NLS-1$ //$NON-NLS-2$
			final Query<?> query = hibernateSession.createQuery(hql);
			query.setParameter("cutoff", removeTime); //$NON-NLS-1$
			final int deletedCount = query.executeUpdate();
			LOGGER.log("Deleted Old Translations: " + deletedCount); //$NON-NLS-1$
			return null;
		}, 3);
	}

	/**
	 * Deletes all translations from the database.
	 */
	public static void deleteAllTranslations() {
		HibernateUtil.createSessionAndExecuteTransactionWithRetry(hibernateSession -> {
			final String hql = "DELETE FROM " + TranslatorEntity.class.getSimpleName(); //$NON-NLS-1$
			final Query<?> query = hibernateSession.createQuery(hql);
			final int deletedCount = query.executeUpdate();
			LOGGER.log("Deleted All Translations: " + deletedCount); //$NON-NLS-1$
			return null;
		}, 3);
	}

	/**
	 * Utility class for managing Hibernate sessions and transactions and ids.
	 */
	static class HibernateUtil {
		// Default config from jar resource paths
		private static final String cfg = ToConfigFiles.libhibernate;
		// Session factory used to create sessions
		static SessionFactory sessionFactory;
		// Secure random instance for generating unique IDs
		static final SecureRandom secureRandom = new SecureRandom();

		/**
		 * Generates a unique id for this entity type. Ensures the generated id does not
		 * already exist in the database.
		 *
		 * @param entityClass The class of the entity (used to check existence)
		 * @return A unique string id
		 */
		static String generateUniqueID(final Class<?> entityClass) {
			final String CHARACTERS = "abcdefghijklmnopqrstuvqxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"; //$NON-NLS-1$
			final int id_Length = 7;
			String id;
			do {
				final StringBuilder sb = new StringBuilder(
						id_Length);
				for (int i = 0; i < id_Length; i++) {
					final int randomIndex = secureRandom.nextInt(CHARACTERS.length());
					final char randomChar = CHARACTERS.charAt(randomIndex);
					sb.append(randomChar);
				}
				id = sb.toString();
			} while (idExists(entityClass, id));
			return id;
		}

		/**
		 * Checks if an id already exists in the database.
		 *
		 * @param entityClass The entity class to check
		 * @param Id          The id to look for
		 * @return True if the id exists, false otherwise
		 */
		static boolean idExists(final Class<?> entityClass, final String Id) {
			return HibernateUtil.createSessionAndExecuteTransactionWithRetry(hibernateSession -> {
				final String entityName = entityClass.getSimpleName();
				final String hql = "SELECT COUNT(e) FROM " + entityName + " e WHERE e." + Column_id + " = :id"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				final Query<Long> query = hibernateSession.createQuery(hql, Long.class);
				query.setParameter("id", Id); //$NON-NLS-1$
				final Long count = query.uniqueResult();
				return Boolean.valueOf(count.longValue() > 0);
			}, 3).booleanValue();
		}

		/**
		 * Initializes the Hibernate session factory.
		 *
		 * @return The initialized session factory
		 */
		static synchronized SessionFactory initSessionFactory() {
			try {
				final Configuration configuration = new Configuration();
				if (Translator.libhiberbernate != null) {
					configuration
							.configure(Thread.currentThread().getContextClassLoader().getResource(Translator.libhiberbernate));
				} else {
					configuration.configure(cfg);
				}
				LOGGER.log("Initializing Hibernate Session Factory: " + TranslatorEntity.class.getName()); //$NON-NLS-1$
				configuration.addAnnotatedClass(TranslatorEntity.class);
				final SessionFactory UniqueSessionFactory = configuration.buildSessionFactory();
				LOGGER.log("Initialized successfully."); //$NON-NLS-1$
				return UniqueSessionFactory;
			} catch (ExceptionInInitializerError | Exception e) {
				LOGGER.log("Error initializing Hibernate Session Factory: " + TranslatorEntity.class.getName(), e); //$NON-NLS-1$
				return null;
			}
		}

		/**
		 * Drops a table from the database.
		 *
		 * @param hibernateSession The current Hibernate session
		 * @param simpleTableName  The simple name of the table to drop
		 * @return True if successful
		 */
		static synchronized void dropTable(final Session hibernateSession, final String simpleTableName) {
			final String sql = "DROP TABLE IF EXISTS " + simpleTableName; //$NON-NLS-1$
			hibernateSession.createNativeQuery(sql).executeUpdate();
			LOGGER.log("Table Deleted"); //$NON-NLS-1$
		}

		/**
		 * Shuts down all Hibernate session factories and deregisters the JDBC driver.
		 */
		static synchronized void shutdown() {
			try {
				sessionFactory.close();
				LOGGER.log("SessionFactories Shutdown"); //$NON-NLS-1$
			} catch (final Exception e) {
				LOGGER.log("Error During SessionFactorys Shutdown: ", e); //$NON-NLS-1$
			}
			try {
				boolean driverFound = false;
				Driver targetDriver = null;
				Enumeration<Driver> drivers = DriverManager.getDrivers();
				while (drivers.hasMoreElements()) {
					Driver d = drivers.nextElement();
					if (d.acceptsURL("jdbc:mariadb://")) { //$NON-NLS-1$
						driverFound = true;
						targetDriver = d;
						break;
					}
				}
				if (driverFound && targetDriver != null) {
					final ClassLoader cl = Thread.currentThread().getContextClassLoader();
					if (targetDriver.getClass().getClassLoader() == cl) {
						DriverManager.deregisterDriver(targetDriver);
						LOGGER.log("Unregistered JDBC Driver: " + targetDriver); //$NON-NLS-1$
					} else {
						LOGGER.log("Driver Exists But Not Deregistered (Class Loader Mismatch)"); //$NON-NLS-1$
					}
				} else {
					LOGGER.log("MariaDB Driver Not Found – Nothing To Deregister"); //$NON-NLS-1$
				}
			} catch (Exception e) {
				LOGGER.log("Unexpected Error While Checking/Deregistering MariaDB Driver", e); //$NON-NLS-1$
			}
		}

		/**
		 * Executes an action within a Hibernate session with retry logic for optimistic
		 * locking.
		 *
		 * @param action     The function to execute
		 * @param maxRetries Maximum number of retries
		 * @param <T>        Return type of the action
		 * @return Result of the action
		 * @throws IllegalStateException if all retries fail
		 */
		static <T> T createSessionAndExecuteTransactionWithRetry(final Function<Session, T> action, final int maxRetries) {
			int retryCount = 0;
			while (retryCount <= maxRetries) {
				Transaction transaction = null;
				try (Session hibernateSession = sessionFactory.openSession()) {
					transaction = hibernateSession.beginTransaction();
					final T result = action.apply(hibernateSession);
					transaction.commit();
					return result;
				} catch (final Exception e) {
					if (!(e instanceof StaleObjectStateException) && !(e instanceof LockAcquisitionException)) {
						if ((transaction != null) && transaction.isActive()) {
							transaction.rollback();
						}
						if (e instanceof HibernateException) {
							LOGGER.log(
									"Hibernate Error During Transaction: ", //$NON-NLS-1$
									e);
						} else {
							LOGGER.log(
									"Unexpected Error During Transaction: ", //$NON-NLS-1$
									e);
						}
						throw e;
					}
					retryCount++;
					if ((transaction != null) && transaction.isActive()) {
						try {
							transaction.rollback();
						} catch (final Exception rollbackException) {
							LOGGER.log(
									"Error During Transaction Rollback", //$NON-NLS-1$
									rollbackException);
							throw rollbackException;
						}
					}
					if (retryCount > maxRetries) {
						LOGGER.log(
								"Max Retries Reached For Optimistic Lock Conflict: ", //$NON-NLS-1$
								e);
						throw e;
					}
					LOGGER.log(
							"Optimistic Locking Conflict Detected. Retrying... Attempt " + retryCount, //$NON-NLS-1$
							e);
					try {
						Thread.sleep(retryCount * 100L);
					} catch (final InterruptedException ie) {
						LOGGER.log(
								"Unexpected Error During Maintenance Thread Sleep: ", //$NON-NLS-1$
								ie);
						Thread.currentThread().interrupt();
						break;
					}
				}
			}
			throw new IllegalStateException(
					"Transaction Failed After Retries Attempted"); //$NON-NLS-1$
		}
	}
}
