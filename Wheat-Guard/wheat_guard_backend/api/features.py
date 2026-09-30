import numpy as np
from PIL import Image

def extract_features(img):
    """
    Extracts a 64-dimensional feature vector from a PIL Image representing color
    statistics, color histograms (RGB & HSV), and texture gradients.
    """
    # Resize to standard dimensions
    img = img.resize((128, 128))
    img_np = np.array(img).astype(np.float32)
    
    # Ensure image has exactly 3 channels
    if img_np.ndim == 2:
        img_np = np.stack([img_np, img_np, img_np], axis=-1)
    elif img_np.shape[2] > 3:
        img_np = img_np[:, :, :3]
        
    r, g, b = img_np[:, :, 0], img_np[:, :, 1], img_np[:, :, 2]
    
    # 1. RGB statistical metrics
    r_mean, r_std = np.mean(r), np.std(r)
    g_mean, g_std = np.mean(g), np.std(g)
    b_mean, b_std = np.mean(b), np.std(b)
    
    # 2. RGB color histograms (8 bins per channel)
    r_hist, _ = np.histogram(r, bins=8, range=(0, 255))
    g_hist, _ = np.histogram(g, bins=8, range=(0, 255))
    b_hist, _ = np.histogram(b, bins=8, range=(0, 255))
    
    # Normalize histograms to ensure sum is 1.0
    r_sum = np.sum(r_hist)
    g_sum = np.sum(g_hist)
    b_sum = np.sum(b_hist)
    
    r_hist = r_hist / (r_sum if r_sum > 0 else 1.0)
    g_hist = g_hist / (g_sum if g_sum > 0 else 1.0)
    b_hist = b_hist / (b_sum if b_sum > 0 else 1.0)
    
    # 3. Dynamic HSV conversion (Vectorized implementation without OpenCV dependency)
    r_n = r / 255.0
    g_n = g / 255.0
    b_n = b / 255.0
    
    cmax = np.maximum(np.maximum(r_n, g_n), b_n)
    cmin = np.minimum(np.minimum(r_n, g_n), b_n)
    diff = cmax - cmin
    
    # Hue calculation
    h = np.zeros_like(cmax)
    idx_r = (cmax == r_n) & (diff != 0)
    h[idx_r] = (60 * ((g_n[idx_r] - b_n[idx_r]) / diff[idx_r]) + 360) % 360
    
    idx_g = (cmax == g_n) & (diff != 0)
    h[idx_g] = (60 * ((b_n[idx_g] - r_n[idx_g]) / diff[idx_g]) + 120) % 360
    
    idx_b = (cmax == b_n) & (diff != 0)
    h[idx_b] = (60 * ((r_n[idx_b] - g_n[idx_b]) / diff[idx_b]) + 240) % 360
    
    # Saturation calculation
    s = np.zeros_like(cmax)
    idx_s = (cmax != 0)
    s[idx_s] = (diff[idx_s] / cmax[idx_s]) * 100.0
    
    # Value calculation
    v = cmax * 100.0
    
    # HSV statistical metrics
    h_mean, h_std = np.mean(h), np.std(h)
    s_mean, s_std = np.mean(s), np.std(s)
    v_mean, v_std = np.mean(v), np.std(v)
    
    # HSV histograms (8 bins per channel)
    h_hist, _ = np.histogram(h, bins=8, range=(0, 360))
    s_hist, _ = np.histogram(s, bins=8, range=(0, 100))
    v_hist, _ = np.histogram(v, bins=8, range=(0, 100))
    
    # Normalize histograms
    h_sum = np.sum(h_hist)
    s_sum = np.sum(s_hist)
    v_sum = np.sum(v_hist)
    
    h_hist = h_hist / (h_sum if h_sum > 0 else 1.0)
    s_hist = s_hist / (s_sum if s_sum > 0 else 1.0)
    v_hist = v_hist / (v_sum if v_sum > 0 else 1.0)
    
    # 4. Greyscale Luma and Edge Texture features
    gray = 0.299 * r + 0.587 * g + 0.114 * b
    gray_mean, gray_std = np.mean(gray), np.std(gray)
    
    # Variances of horizontal and vertical gradients
    grad_x = np.diff(gray, axis=1)
    grad_y = np.diff(gray, axis=0)
    grad_x_var = np.var(grad_x)
    grad_y_var = np.var(grad_y)
    
    # Concatenate to form a 64-dimensional feature vector
    features = np.concatenate([
        [r_mean, r_std, g_mean, g_std, b_mean, b_std],
        r_hist, g_hist, b_hist,
        [h_mean, h_std, s_mean, s_std, v_mean, v_std],
        h_hist, s_hist, v_hist,
        [gray_mean, gray_std, grad_x_var, grad_y_var]
    ])
    
    return features
