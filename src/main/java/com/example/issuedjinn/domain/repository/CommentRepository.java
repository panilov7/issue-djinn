package com.example.issuedjinn.domain.repository;

import com.example.issuedjinn.domain.entity.Comment;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repository for Comment entities.
 */
@ApplicationScoped
public class CommentRepository implements PanacheRepositoryBase<Comment, Long> {
}
