package com.groupa13.disasterrelief.auth;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.groupa13.disasterrelief.dao.operations.AppUserDao;
import com.groupa13.disasterrelief.db.Database;
import com.groupa13.disasterrelief.db.DatabaseException;
import com.groupa13.disasterrelief.model.operations.AppUser;
import com.groupa13.disasterrelief.service.core.ServiceResult;
import com.groupa13.disasterrelief.util.UserSession;

/**
 * Handles application authentication, BCrypt verification, active status checking,
 * and user session initialization.
 */
public class AuthService {

    private static final Logger LOGGER = Logger.getLogger(AuthService.class.getName());

    private final AppUserDao dao;

    public AuthService() {
        this(new AppUserDao());
    }

    public AuthService(AppUserDao dao) {
        this.dao = Objects.requireNonNull(dao, "dao");
    }

    /**
     * Authenticates a user against stored database credentials.
     * On success, populates and returns the UserSession singleton.
     */
    public ServiceResult<UserSession> authenticate(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return ServiceResult.failure("Enter user ID and password");
        }

        try {
            Optional<AppUser> userOpt = Database.inTransaction(connection -> dao.findByUsername(connection, username.trim()));

            if (userOpt.isEmpty()) {
                return ServiceResult.failure("Invalid user ID or password");
            }

            AppUser user = userOpt.get();

            if (!user.isActive()) {
                return ServiceResult.failure("Account is inactive; contact system administrator");
            }

            if (!PasswordHasher.verify(password, user.passwordHash())) {
                return ServiceResult.failure("Invalid user ID or password");
            }

            UserSession session = UserSession.getInstance();
            session.setUserId(String.valueOf(user.userId()));
            session.setUserName(user.username());
            session.setRole(user.role());
            session.setAssignedZoneId(user.assignedZoneId());

            return ServiceResult.success(session, "Login successful");
        } catch (DatabaseException exception) {
            LOGGER.log(Level.SEVERE, "Database error during authentication", exception);
            return ServiceResult.failure("Unable to connect to authentication service");
        }
    }
}
