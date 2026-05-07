import { useState, useEffect } from 'react';
import { Box, Stack, Typography } from '@mui/material';
import { NOIMAGE } from '../constants';

export default function ImageGallery({ images = [], alt = '', placeholder = null }) {
  const [mainIdx, setMainIdx] = useState(0);

  useEffect(() => {
    setMainIdx(0);
  }, [images]);

  if (placeholder) {
    return (
      <Box
        sx={{
          width: '100%',
          height: 320,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          bgcolor: '#f5f5f5',
          borderRadius: 1,
        }}
      >
        <Typography color="text.secondary" variant="body2">{placeholder}</Typography>
      </Box>
    );
  }

  return (
    <Box>
      <Box
        component="img"
        src={images[mainIdx] ?? NOIMAGE}
        alt={alt}
        data-testid="image-gallery-main"
        sx={{
          width: '100%',
          height: 320,
          objectFit: 'contain',
          bgcolor: '#f5f5f5',
          borderRadius: 1,
        }}
      />
      {images.length > 1 && (
        <Stack direction="row" spacing={1} sx={{ mt: 1, flexWrap: 'wrap' }}>
          {images.map((img, i) => (
            <Box
              key={i}
              component="img"
              src={img}
              alt=""
              data-testid={`image-gallery-thumb-${i}`}
              onClick={() => setMainIdx(i)}
              sx={{
                width: 56,
                height: 56,
                objectFit: 'contain',
                border: mainIdx === i ? '2px solid' : '1px solid #ddd',
                borderColor: mainIdx === i ? 'primary.main' : '#ddd',
                borderRadius: 1,
                cursor: 'pointer',
                bgcolor: '#f5f5f5',
              }}
            />
          ))}
        </Stack>
      )}
    </Box>
  );
}
