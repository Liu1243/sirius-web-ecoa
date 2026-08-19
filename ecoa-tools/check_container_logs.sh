#!/bin/bash
# Script to check ecoa-tools container logs for INTEGRATION mode issues

echo "========================================"
echo "ECOA Tools Container Log Checker"
echo "========================================"

# Check if container is running
echo ""
echo "1. Container Status:"
docker ps | grep ecoa-tools || echo "   ✗ ecoa-tools container not running"

# Check recent logs
echo ""
echo "2. Recent Logs (last 50 lines):"
docker logs --tail 50 ecoa-tools 2>&1 | grep -E "INTEGRATION|selectedVersions|MSCIGT|Skeleton|CSMGVT|mode" || echo "   No relevant logs found"

# Check for specific patterns
echo ""
echo "3. Checking for INTEGRATION mode logs:"
docker logs ecoa-tools 2>&1 | grep -c "INTEGRATION mode" && echo "   Found INTEGRATION mode entries" || echo "   No INTEGRATION mode entries"

echo ""
echo "4. Checking for MSCIGT execution:"
docker logs ecoa-tools 2>&1 | grep -c "MSCIGT.*skeleton" && echo "   Found MSCIGT skeleton entries" || echo "   No MSCIGT skeleton entries"

echo ""
echo "5. Checking for selectedVersions:"
docker logs ecoa-tools 2>&1 | grep -c "selectedVersions" && echo "   Found selectedVersions entries" || echo "   No selectedVersions entries"

echo ""
echo "6. Log file listing inside container:"
docker exec ecoa-tools ls -la /app/logs/ 2>/dev/null || echo "   ✗ Cannot access logs directory"

# Run diagnostic script inside container
echo ""
echo "7. Running diagnostic tests inside container:"
docker exec ecoa-tools python /app/test_integration_in_container.py 2>&1 || echo "   ✗ Failed to run tests"

echo ""
echo "========================================"
echo "Log check completed"
echo "========================================"
