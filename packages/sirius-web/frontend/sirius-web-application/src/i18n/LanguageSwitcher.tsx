import { Translate } from '@mui/icons-material';
import { Box, Button, Menu, MenuItem, Popover, Tooltip, Typography } from '@mui/material';
import { MouseEvent, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';

export const LanguageSwitcher = () => {
  const { i18n, t } = useTranslation();
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const [guideAnchorEl, setGuideAnchorEl] = useState<null | HTMLElement>(null);
  const open = Boolean(anchorEl);
  const openGuide = Boolean(guideAnchorEl);

  const handleClick = (event: MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
    // Close guide if it's open when user clicks the button
    if (openGuide) {
      handleCloseGuide();
    }
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const changeLanguage = (lng: string) => {
    i18n.changeLanguage(lng);
    handleClose();
  };

  const handleCloseGuide = () => {
    setGuideAnchorEl(null);
    localStorage.setItem('hasSeenLanguageGuide', 'true');
  };

  // Check if guide should be shown
  useEffect(() => {
    const hasSeenGuide = localStorage.getItem('hasSeenLanguageGuide');
    if (!hasSeenGuide) {
      // Small delay to ensure the button is rendered and to draw attention
      const timer = setTimeout(() => {
        const element = document.getElementById('language-switcher-btn');
        if (element) {
          setGuideAnchorEl(element);
        }
      }, 1000);
      return () => clearTimeout(timer);
    }
    return undefined;
  }, []);

  return (
    <Box>
      <Tooltip title={t('languageSwitcher.changeLanguage')}>
        <Button id="language-switcher-btn" onClick={handleClick} color="inherit" sx={{ minWidth: 'auto', p: 1 }}>
          <Translate />
          <Box component="span" sx={{ ml: 1, display: { xs: 'none', md: 'block' } }}>
            {i18n.language === 'zh' ? '中文' : 'English'}
          </Box>
        </Button>
      </Tooltip>
      <Menu
        anchorEl={anchorEl}
        open={open}
        onClose={handleClose}
        transformOrigin={{ horizontal: 'right', vertical: 'top' }}
        anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}>
        <MenuItem onClick={() => changeLanguage('en')} selected={i18n.language === 'en'}>
          English
        </MenuItem>
        <MenuItem onClick={() => changeLanguage('zh')} selected={i18n.language === 'zh'}>
          中文
        </MenuItem>
      </Menu>
      <Popover
        open={openGuide}
        anchorEl={guideAnchorEl}
        onClose={handleCloseGuide}
        anchorOrigin={{
          vertical: 'bottom',
          horizontal: 'right',
        }}
        transformOrigin={{
          vertical: 'top',
          horizontal: 'right',
        }}>
        <Box sx={{ p: 2, maxWidth: 300 }}>
          <Typography variant="body2" sx={{ mb: 1 }}>
            {t('languageSwitcher.guideText')}
          </Typography>
          <Button size="small" variant="contained" onClick={handleCloseGuide} fullWidth>
            {t('languageSwitcher.guideButton')}
          </Button>
        </Box>
      </Popover>
    </Box>
  );
};
