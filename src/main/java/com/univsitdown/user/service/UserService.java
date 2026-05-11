package com.univsitdown.user.service;

import com.univsitdown.user.domain.User;
import com.univsitdown.user.dto.UpdateUserRequest;
import com.univsitdown.user.dto.UserResponse;
import com.univsitdown.user.exception.UserNotFoundException;
import com.univsitdown.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateUser(UUID userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        user.update(request.name(), request.phone(), request.affiliation());
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateProfileImage(UUID userId, MultipartFile file) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        String ext = getExtension(file.getOriginalFilename());
        String filename = userId + "_" + UUID.randomUUID() + "." + ext;
        Path dir  = Paths.get(uploadDir, "profiles", userId.toString());
        Path dest = dir.resolve(filename);

        try {
            Files.createDirectories(dir);
            file.transferTo(dest.toFile());
        } catch (IOException e) {
            throw new RuntimeException("파일 저장 실패", e);
        }

        String url = "/uploads/profiles/" + userId + "/" + filename;
        user.updateProfileImageUrl(url);
        return UserResponse.from(user);
    }

    private String getExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) return "jpg";
        return originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
    }
}
