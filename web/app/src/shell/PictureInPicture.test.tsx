// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useUi } from '@/state/ui';
import {
  closePictureInPicture,
  copyStylesToWindow,
  isDocumentPipSupported,
  openPictureInPicture,
  togglePictureInPicture,
} from './PictureInPicture';

describe('PictureInPicture', () => {
  let mockPipWindow: Window & { closed: boolean; addEventListener: any; removeEventListener: any; close: any; focus: any; document: Document };

  beforeEach(() => {
    useUi.getState().setPipOpen(false);

    // Create a mock PiP window
    const pipDoc = document.implementation.createHTMLDocument('PiP');
    mockPipWindow = {
      closed: false,
      document: pipDoc,
      close: vi.fn(() => {
        mockPipWindow.closed = true;
      }),
      focus: vi.fn(),
      addEventListener: vi.fn((event: string, handler: any) => {
        if (event === 'pagehide') {
          (mockPipWindow as any)._pagehideHandler = handler;
        }
      }),
      removeEventListener: vi.fn(),
    } as any;
  });

  afterEach(() => {
    closePictureInPicture();
    delete (window as any).documentPictureInPicture;
    vi.restoreAllMocks();
  });

  describe('isDocumentPipSupported', () => {
    it('returns false when window.documentPictureInPicture is undefined', () => {
      delete (window as any).documentPictureInPicture;
      expect(isDocumentPipSupported()).toBe(false);
    });

    it('returns true when window.documentPictureInPicture is present', () => {
      (window as any).documentPictureInPicture = {
        requestWindow: vi.fn(),
        window: null,
      };
      expect(isDocumentPipSupported()).toBe(true);
    });
  });

  describe('openPictureInPicture and closePictureInPicture', () => {
    it('does nothing if Document PiP is unsupported', async () => {
      delete (window as any).documentPictureInPicture;
      await openPictureInPicture();
      expect(useUi.getState().pipOpen).toBe(false);
    });

    it('opens window with width and height and updates pipOpen state', async () => {
      const requestWindow = vi.fn().mockResolvedValue(mockPipWindow);
      (window as any).documentPictureInPicture = {
        requestWindow,
        window: null,
      };

      await openPictureInPicture();

      expect(requestWindow).toHaveBeenCalledWith({ width: 340, height: 460 });
      expect(useUi.getState().pipOpen).toBe(true);
    });

    it('focuses existing window if already open', async () => {
      const requestWindow = vi.fn().mockResolvedValue(mockPipWindow);
      (window as any).documentPictureInPicture = {
        requestWindow,
        window: null,
      };

      await openPictureInPicture();
      expect(requestWindow).toHaveBeenCalledTimes(1);

      await openPictureInPicture();
      expect(requestWindow).toHaveBeenCalledTimes(1);
      expect(mockPipWindow.focus).toHaveBeenCalled();
    });

    it('closes window and sets pipOpen to false', async () => {
      const requestWindow = vi.fn().mockResolvedValue(mockPipWindow);
      (window as any).documentPictureInPicture = {
        requestWindow,
        window: null,
      };

      await openPictureInPicture();
      expect(useUi.getState().pipOpen).toBe(true);

      closePictureInPicture();
      expect(mockPipWindow.close).toHaveBeenCalled();
      expect(useUi.getState().pipOpen).toBe(false);
    });

    it('toggles PiP window open and closed', async () => {
      const requestWindow = vi.fn().mockResolvedValue(mockPipWindow);
      (window as any).documentPictureInPicture = {
        requestWindow,
        window: null,
      };

      await togglePictureInPicture();
      expect(useUi.getState().pipOpen).toBe(true);

      await togglePictureInPicture();
      expect(mockPipWindow.close).toHaveBeenCalled();
      expect(useUi.getState().pipOpen).toBe(false);
    });

    it('handles window pagehide event to update pipOpen state', async () => {
      const requestWindow = vi.fn().mockResolvedValue(mockPipWindow);
      (window as any).documentPictureInPicture = {
        requestWindow,
        window: null,
      };

      await openPictureInPicture();
      expect(useUi.getState().pipOpen).toBe(true);

      // Trigger the registered pagehide listener
      (mockPipWindow as any)._pagehideHandler();
      expect(useUi.getState().pipOpen).toBe(false);
    });
  });

  describe('copyStylesToWindow', () => {
    it('transfers link tags and style tags into target document head', () => {
      const style = document.createElement('style');
      style.textContent = '.custom { color: red; }';
      document.head.appendChild(style);

      const targetDoc = document.implementation.createHTMLDocument('Target');
      copyStylesToWindow(targetDoc);

      const targetStyles = targetDoc.head.querySelectorAll('style');
      expect(targetStyles.length).toBeGreaterThanOrEqual(1);
      expect(targetDoc.documentElement.getAttribute('data-theme')).toBe('dark');

      style.remove();
    });
  });
});
