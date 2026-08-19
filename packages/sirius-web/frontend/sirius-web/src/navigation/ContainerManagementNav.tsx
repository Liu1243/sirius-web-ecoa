import Button from '@mui/material/Button';
import { useNavigate } from 'react-router-dom';
import ComputerIcon from '@mui/icons-material/Computer';

export const ContainerManagementNav = () => {
  const navigate = useNavigate();

  return (
    <Button color="inherit" size="small" startIcon={<ComputerIcon />} onClick={() => navigate('/distributed-debug')}>
      容器管理
    </Button>
  );
};
