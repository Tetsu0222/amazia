package com.example.auth.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "role_permissions")
public class RolePermission {

    @EmbeddedId
    private RolePermissionId id = new RolePermissionId();

    @ManyToOne
    @MapsId("roleId")
    @JoinColumn(name = "role_id")
    private Role role;

    @ManyToOne
    @MapsId("permissionId")
    @JoinColumn(name = "permission_id")
    private Permission permission;

    @Embeddable
    public static class RolePermissionId implements java.io.Serializable {
        private Long roleId;
        private Long permissionId;

        public Long getRoleId() { return roleId; }
        public void setRoleId(Long roleId) { this.roleId = roleId; }
        public Long getPermissionId() { return permissionId; }
        public void setPermissionId(Long permissionId) { this.permissionId = permissionId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RolePermissionId)) return false;
            RolePermissionId that = (RolePermissionId) o;
            return java.util.Objects.equals(roleId, that.roleId) &&
                   java.util.Objects.equals(permissionId, that.permissionId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(roleId, permissionId);
        }
    }

    public RolePermissionId getId() { return id; }
    public Role getRole() { return role; }
    public Permission getPermission() { return permission; }
}
