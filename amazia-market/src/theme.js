import { createTheme } from '@mui/material/styles';

const theme = createTheme({
  palette: {
    primary: {
      main: '#1976d2',
    },
    header: {
      main: '#0F1A2B',
      sub: '#1B2838',
      text: '#FFFFFF',
      hoverBorder: '#FFFFFF',
    },
    accent: {
      main: '#F0A93B',
      contrastText: '#0F1A2B',
    },
    link: {
      main: '#0066C0',
    },
    border: {
      card: '#DDDDDD',
    },
    background: {
      default: '#EAEDED',
      paper: '#FFFFFF',
    },
  },
});

export default theme;
