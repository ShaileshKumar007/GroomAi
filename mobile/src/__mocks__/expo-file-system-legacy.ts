export enum FileSystemUploadType {
  BINARY_CONTENT = 0,
  MULTIPART = 1,
}

export const getInfoAsync = jest.fn(async () => ({
  exists: true,
  size: 1024,
}));

export const deleteAsync = jest.fn(async () => {});

export const uploadAsync = jest.fn(async () => ({
  status: 200,
  body: JSON.stringify({ message: 'Success' }),
  headers: {},
}));
