-- Migration script to fix audit fields changed from String to Long
-- Run this script on your PostgreSQL database to handle existing data

-- Update users table: set NULL for non-numeric values in created_by/updated_by
UPDATE users 
SET created_by = NULL 
WHERE created_by IS NOT NULL AND created_by ~ '[^0-9]';

UPDATE users 
SET updated_by = NULL 
WHERE updated_by IS NOT NULL AND updated_by ~ '[^0-9]';

-- Update employee_profiles table: set NULL for non-numeric values in created_by/updated_by  
UPDATE employee_profiles 
SET created_by = NULL 
WHERE created_by IS NOT NULL AND created_by ~ '[^0-9]';

UPDATE employee_profiles 
SET updated_by = NULL 
WHERE updated_by IS NOT NULL AND updated_by ~ '[^0-9]';

-- Convert valid numeric strings to actual BIGINT
UPDATE users 
SET created_by = CAST(created_by AS BIGINT) 
WHERE created_by IS NOT NULL AND created_by ~ '^[0-9]+$';

UPDATE users 
SET updated_by = CAST(updated_by AS BIGINT) 
WHERE updated_by IS NOT NULL AND updated_by ~ '^[0-9]+$';

UPDATE employee_profiles 
SET created_by = CAST(created_by AS BIGINT) 
WHERE created_by IS NOT NULL AND created_by ~ '^[0-9]+$';

UPDATE employee_profiles 
SET updated_by = CAST(updated_by AS BIGINT) 
WHERE updated_by IS NOT NULL AND updated_by ~ '^[0-9]+$';

-- Verify the changes
SELECT 'users table audit fields:' as info;
SELECT COUNT(*) as total_users, 
       COUNT(created_by) as with_created_by, 
       COUNT(updated_by) as with_updated_by 
FROM users;

SELECT 'employee_profiles table audit fields:' as info;
SELECT COUNT(*) as total_profiles,
       COUNT(created_by) as with_created_by,
       COUNT(updated_by) as with_updated_by
FROM employee_profiles;
