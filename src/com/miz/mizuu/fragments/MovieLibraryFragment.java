package com.miz.mizuu.fragments;

import java.io.File;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;

import jcifs.smb.SmbFile;
import android.app.ActionBar;
import android.app.ActionBar.OnNavigationListener;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.SectionIndexer;
import android.widget.TextView;

import com.miz.functions.AsyncTask;
import com.miz.functions.CoverItem;
import com.miz.functions.FileSource;
import com.miz.functions.ImageLoadingErrorListener;
import com.miz.functions.MediumMovie;
import com.miz.functions.MizLib;
import com.miz.functions.SQLiteCursorLoader;
import com.miz.functions.SpinnerItem;
import com.miz.mizuu.DbAdapter;
import com.miz.mizuu.DbAdapterSources;
import com.miz.mizuu.DbHelper;
import com.miz.mizuu.MizuuApplication;
import com.miz.mizuu.MovieCollection;
import com.miz.mizuu.MovieDetails;
import com.miz.mizuu.Preferences;
import com.miz.mizuu.R;
import com.miz.mizuu.Update;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;

public class MovieLibraryFragment extends Fragment implements OnNavigationListener, OnSharedPreferenceChangeListener {

	public static final int MAIN = 0, OTHER = 1;
	public static final String ALL = "ALL", AVAILABLE = "AVAILABLE", FAVORITES = "FAVORITES", WATCHLIST = "WATCHLIST", COLLECTIONS = "COLLECTIONS";

	private SharedPreferences settings;
	private int mImageThumbSize, mImageThumbSpacing;
	private LoaderAdapter mAdapter;
	private ArrayList<MediumMovie> movies = new ArrayList<MediumMovie>(), shownMovies = new ArrayList<MediumMovie>();
	private GridView mGridView = null;
	private ProgressBar pbar;
	private TextView overviewMessage;
	private Button updateMovieLibrary;
	private boolean showGridTitles, ignorePrefixes, prefsDisableEthernetWiFiCheck, ignoreNfo;
	private ActionBar actionBar;
	private int type;
	private DisplayImageOptions options;
	private ImageLoader imageLoader;
	private ArrayList<SpinnerItem> spinnerItems = new ArrayList<SpinnerItem>();
	private ActionBarSpinner spinnerAdapter;

	/**
	 * Empty constructor as per the Fragment documentation
	 */
	public MovieLibraryFragment() {}

	public static MovieLibraryFragment newInstance(int type) {
		MovieLibraryFragment frag = new MovieLibraryFragment();
		Bundle b = new Bundle();
		b.putInt("type", type);
		frag.setArguments(b);		
		return frag;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {		
		super.onCreate(savedInstanceState);

		type = getArguments().getInt("type");

		setRetainInstance(true);
		setHasOptionsMenu(true);

		setupSpinnerItems();

		// Set OnSharedPreferenceChange listener
		PreferenceManager.getDefaultSharedPreferences(getActivity()).registerOnSharedPreferenceChangeListener(this);

		// Initialize the PreferenceManager variable and preference variable(s)
		settings = PreferenceManager.getDefaultSharedPreferences(getActivity());

		showGridTitles = settings.getBoolean("prefsShowGridTitles", false);
		ignorePrefixes = settings.getBoolean("prefsIgnorePrefixesInTitles", false);
		ignoreNfo = settings.getBoolean("prefsIgnoreNfoFiles", true);
		prefsDisableEthernetWiFiCheck = settings.getBoolean("prefsDisableEthernetWiFiCheck", false);

		mImageThumbSize = getResources().getDimensionPixelSize(R.dimen.image_thumbnail_size);
		mImageThumbSpacing = getResources().getDimensionPixelSize(R.dimen.image_thumbnail_spacing);

		imageLoader = ImageLoader.getInstance();
		options = MizuuApplication.getDefaultCoverLoadingOptions();

		mAdapter = new LoaderAdapter(getActivity());

		forceLoaderLoad();
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		// Setup ActionBar with the action list
		actionBar = getActivity().getActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
		spinnerAdapter = new ActionBarSpinner();
		actionBar.setListNavigationCallbacks(spinnerAdapter, this);

		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mMessageReceiver, new IntentFilter("mizuu-movies-update"));
		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mMessageReceiver, new IntentFilter("mizuu-library-change"));
		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mMessageReceiver, new IntentFilter("mizuu-movie-cover-change"));
	}

	private void setupSpinnerItems() {
		spinnerItems.clear();
		if (type == MAIN)
			spinnerItems.add(new SpinnerItem(getString(R.string.chooserMovies), getString(R.string.choiceAllMovies)));
		else
			spinnerItems.add(new SpinnerItem(getString(R.string.chooserWatchList), getString(R.string.choiceAllMovies)));
		spinnerItems.add(new SpinnerItem(getString(R.string.choiceFavorites), getString(R.string.choiceFavorites)));
		spinnerItems.add(new SpinnerItem(getString(R.string.choiceAvailableFiles), getString(R.string.choiceAvailableFiles)));
		spinnerItems.add(new SpinnerItem(getString(R.string.choiceCollections), getString(R.string.choiceCollections)));
		spinnerItems.add(new SpinnerItem(getString(R.string.choiceWatchedMovies), getString(R.string.choiceWatchedMovies)));
		spinnerItems.add(new SpinnerItem(getString(R.string.choiceUnwatchedMovies), getString(R.string.choiceUnwatchedMovies)));
	}

	private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.filterEquals(new Intent("mizuu-library-change")) || intent.filterEquals(new Intent("mizuu-movie-cover-change"))) {
				clearCaches();
			}

			forceLoaderLoad();
		}
	};

	LoaderCallbacks<Cursor> loaderCallbacks = new LoaderCallbacks<Cursor>() {
		@Override
		public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
			return new SQLiteCursorLoader(getActivity(), DbHelper.getHelper(getActivity()), DbAdapter.DATABASE_TABLE, DbAdapter.SELECT_ALL, "NOT(" + DbAdapter.KEY_TITLE + " = 'MIZ_REMOVED_MOVIE')", null, null, null, DbAdapter.KEY_TITLE + " ASC");
		}

		@Override
		public void onLoadFinished(Loader<Cursor> arg0, Cursor cursor) {
			movies.clear();
			shownMovies.clear();

			try {
				// Do while the cursor can move to the next item in cursor
				while (cursor.moveToNext()) {
					boolean add = true;

					if (type == OTHER && cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_TO_WATCH)).equals("0"))
						add = false;

					if (add)
						movies.add(new MediumMovie(getActivity(),
								cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_ROWID)),
								cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_FILEPATH)),
								cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_TITLE)),
								cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_TMDBID)),
								cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_RATING)),
								cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_RELEASEDATE)),
								cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_GENRES)),
								cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_FAVOURITE)),
								cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_CAST)),
								cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_COLLECTION)),
								cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_EXTRA_2)),
								cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_TO_WATCH)),
								cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_HAS_WATCHED)),
								cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_EXTRA_1)),
								cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_CERTIFICATION)),
								cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_RUNTIME)),
								ignorePrefixes,
								ignoreNfo
								));
				}
			} catch (Exception e) {}
			finally {
				cursor.close();
			}

			if (type == MAIN)
				shownMovies.addAll(movies);
			else {
				int count = movies.size();
				for (int i = 0; i < count; i++)
					if (movies.get(i).toWatch())
						shownMovies.add(movies.get(i));
			}

			showCollectionBasedOnNavigationIndex(actionBar.getSelectedNavigationIndex());
		}

		@Override
		public void onLoaderReset(Loader<Cursor> arg0) {
			movies.clear();
			shownMovies.clear();
			notifyDataSetChanged();

			forceLoaderLoad();
		}
	};

	private void clearCaches() {
		try {
			imageLoader.clearMemoryCache();
			imageLoader.clearDiscCache();
		} catch (Exception e) {}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.image_grid_fragment, container, false);
	}

	@Override
	public void onViewCreated(View v, Bundle savedInstanceState) {
		super.onViewCreated(v, savedInstanceState);

		pbar = (ProgressBar) v.findViewById(R.id.progress);

		overviewMessage = (TextView) v.findViewById(R.id.overviewMessage);

		updateMovieLibrary = (Button) v.findViewById(R.id.addMoviesButton);
		updateMovieLibrary.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				startActivityForResult(getUpdateIntent(), 0);
			}
		});

		mAdapter = new LoaderAdapter(getActivity());

		mGridView = (GridView) v.findViewById(R.id.gridView);
		mGridView.setFastScrollEnabled(true);
		mGridView.setAdapter(mAdapter);

		// Calculate the total column width to set item heights by factor 1.5
		mGridView.getViewTreeObserver().addOnGlobalLayoutListener(
				new ViewTreeObserver.OnGlobalLayoutListener() {
					@Override
					public void onGlobalLayout() {
						if (mAdapter.getNumColumns() == 0) {
							final int numColumns = (int) Math.floor(
									mGridView.getWidth() / (mImageThumbSize + mImageThumbSpacing));
							if (numColumns > 0) {
								final int columnWidth = (mGridView.getWidth() / numColumns) - mImageThumbSpacing;
								mAdapter.setNumColumns(numColumns);
								mAdapter.setItemHeight(columnWidth);
							}
						}
					}
				});
		mGridView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
				Intent intent = new Intent();
				if (actionBar.getSelectedNavigationIndex() == 3) { // Collection
					intent.putExtra("collectionId", shownMovies.get(arg2).getCollectionId());
					intent.putExtra("collectionTitle", shownMovies.get(arg2).getCollection());
					intent.setClass(getActivity(), MovieCollection.class);
					startActivity(intent);
				} else {
					intent.putExtra("rowId", Integer.parseInt(shownMovies.get(arg2).getRowId()));
					intent.setClass(getActivity(), MovieDetails.class);
					startActivityForResult(intent, 0);
				}
			}
		});
		mGridView.setOnScrollListener(MizuuApplication.getPauseOnScrollListener(imageLoader));
	}

	@Override
	public void onResume() {
		super.onResume();

		if (mAdapter != null)
			mAdapter.notifyDataSetChanged();
	}

	@Override
	public void onPause() {
		super.onPause();

		imageLoader.stop();
	}

	@Override
	public void onDestroy() {	
		// Unregister since the activity is about to be closed.
		LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mMessageReceiver);
		PreferenceManager.getDefaultSharedPreferences(getActivity()).unregisterOnSharedPreferenceChangeListener(this);

		super.onDestroy();
	}

	private class LoaderAdapter extends BaseAdapter implements SectionIndexer {

		private LayoutInflater inflater;
		private final Context mContext;
		private int mItemHeight = 0;
		private int mNumColumns = 0;
		private GridView.LayoutParams mImageViewLayoutParams;
		private Object[] sections;

		public LoaderAdapter(Context context) {
			super();
			mContext = context;
			inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			mImageViewLayoutParams = new GridView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		}

		@Override
		public int getCount() {
			return shownMovies.size();
		}

		@Override
		public Object getItem(int position) {
			return shownMovies.get(position).getThumbnail();
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup container) {

			CoverItem holder;
			if (convertView == null) {
				convertView = inflater.inflate(R.layout.grid_item_cover, container, false);
				holder = new CoverItem();
				holder.layout = (RelativeLayout) convertView.findViewById(R.id.cover_layout);
				holder.cover = (ImageView) convertView.findViewById(R.id.cover);
				holder.text = (TextView) convertView.findViewById(R.id.text);
				convertView.setTag(holder);
			} else {
				holder = (CoverItem) convertView.getTag();
			}

			holder.text.setVisibility(TextView.GONE);

			// Check the height matches our calculated column width
			if (holder.layout.getLayoutParams().height != mItemHeight) {
				holder.layout.setLayoutParams(mImageViewLayoutParams);
			}

			if (showGridTitles) {
				holder.text.setVisibility(TextView.VISIBLE);
				if (actionBar.getSelectedNavigationIndex() == 3)
					holder.text.setText(shownMovies.get(position).getCollection());
				else
					holder.text.setText(shownMovies.get(position).getTitle());
			} else {
				holder.text.setVisibility(TextView.GONE);
			}

			if (actionBar.getSelectedNavigationIndex() == 3) {
				if (showGridTitles)
					imageLoader.displayImage("file://" + shownMovies.get(position).getCollectionPoster(), holder.cover, options);
				else
					imageLoader.displayImage("file://" + shownMovies.get(position).getCollectionPoster(), holder.cover, options,
							new ImageLoadingErrorListener(shownMovies.get(position).getCollection(), null, null));
			} else {

				if (!ignoreNfo && shownMovies.get(position).isNetworkFile()) {
					imageLoader.displayImage(shownMovies.get(position).getFilepath() + "<MiZ>" + shownMovies.get(position).getThumbnail(), holder.cover, options,
							new ImageLoadingErrorListener(shownMovies.get(position).getTitle(), null, null));
				} else {
					if (showGridTitles)
						imageLoader.displayImage("file://" + shownMovies.get(position).getThumbnail(), holder.cover, options);
					else
						imageLoader.displayImage("file://" + shownMovies.get(position).getThumbnail(), holder.cover, options,
								new ImageLoadingErrorListener(shownMovies.get(position).getTitle(), null, null));
				}
			}

			return convertView;
		}

		/**
		 * Sets the item height. Useful for when we know the column width so the height can be set
		 * to match.
		 *
		 * @param height
		 */
		public void setItemHeight(int height) {
			mItemHeight = height;
			mImageViewLayoutParams = new GridView.LayoutParams(LayoutParams.MATCH_PARENT, (int) (mItemHeight * 1.5));
		}

		public void setNumColumns(int numColumns) {
			mNumColumns = numColumns;
		}

		public int getNumColumns() {
			return mNumColumns;
		}

		@Override
		public int getPositionForSection(int section) {
			return section;
		}

		@Override
		public int getSectionForPosition(int position) {
			return position;
		}

		@Override
		public Object[] getSections() {
			return sections;
		}

		@Override
		public void notifyDataSetChanged() {

			ArrayList<MediumMovie> tempMovies = new ArrayList<MediumMovie>(shownMovies);
			sections = new Object[tempMovies.size()];

			String SORT_TYPE = settings.getString("prefsSorting", "sortTitle");
			if (SORT_TYPE.equals("sortRating")) {
				DecimalFormat df = new DecimalFormat("#.#");
				for (int i = 0; i < sections.length; i++)
					sections[i] = df.format(tempMovies.get(i).getRawRating());
			} else if (SORT_TYPE.equals("sortWeightedRating")) {
				DecimalFormat df = new DecimalFormat("#.#");
				for (int i = 0; i < sections.length; i++)
					sections[i] = df.format(tempMovies.get(i).getWeightedRating());
			} else if (SORT_TYPE.equals("sortDuration")) {
				String hour = getResources().getQuantityString(R.plurals.hour, 1, 1).substring(0,1);
				String minute = getResources().getQuantityString(R.plurals.minute, 1, 1).substring(0,1);
				
				for (int i = 0; i < sections.length; i++)
					sections[i] = MizLib.getRuntimeInMinutesOrHours(tempMovies.get(i).getRuntime(), hour, minute);
			} else {
				String temp = "";
				for (int i = 0; i < sections.length; i++)
					if (!MizLib.isEmpty(tempMovies.get(i).getTitle())) {
						temp = tempMovies.get(i).getTitle().substring(0,1);
						if (Character.isLetter(temp.charAt(0)))
							sections[i] = tempMovies.get(i).getTitle().substring(0,1);
						else
							sections[i] = "#";
					} else
						sections[i] = "";
			}

			tempMovies.clear();
			tempMovies = null;

			super.notifyDataSetChanged();
		}
	}

	private void notifyDataSetChanged() {
		if (mAdapter != null)
			mAdapter.notifyDataSetChanged();

		if (spinnerAdapter != null)
			spinnerAdapter.notifyDataSetChanged();

		try {
			if (movies.size() == 0) {
				overviewMessage.setVisibility(View.VISIBLE);

				if (type == MAIN) {
					if (MizLib.isMovieLibraryBeingUpdated(getActivity())) {
						overviewMessage.setText(getString(R.string.updatingLibraryDescription));
						updateMovieLibrary.setVisibility(View.GONE);
					} else {
						overviewMessage.setText(getString(R.string.noMoviesInLibrary));
						updateMovieLibrary.setVisibility(View.VISIBLE);
					}
				} else {
					overviewMessage.setText(getString(R.string.noMoviesOnWatchlist));
					updateMovieLibrary.setVisibility(View.GONE);
				}
			} else {
				overviewMessage.setVisibility(View.GONE);
				updateMovieLibrary.setVisibility(View.GONE);
			}
		} catch (Exception e) {} // Can happen sometimes, I guess...
	}

	@Override
	public boolean onNavigationItemSelected(int itemPosition, long itemId) {
		showCollectionBasedOnNavigationIndex(itemPosition);
		return true;
	}

	private void showCollectionBasedOnNavigationIndex(int itemPosition) {

		if (spinnerAdapter != null)
			spinnerAdapter.notifyDataSetChanged(); // To show "0 movies" when loading

		switch (itemPosition) {
		case 0:
			showAllMovies();
			break;
		case 1:
			showFavorites();
			break;
		case 2:
			showAvailableFiles();
			break;
		case 3:
			showCollections();
			break;
		case 4:
			showWatchedMovies(true);
			break;
		case 5:
			showWatchedMovies(false);
			break;
		}
	}

	private void showAllMovies() {
		showProgressBar();

		shownMovies.clear();

		shownMovies.addAll(movies);

		sortMovies();

		notifyDataSetChanged();

		hideProgressBar();
	}

	private void showFavorites() {
		showProgressBar();

		shownMovies.clear();

		for (int i = 0; i < movies.size(); i++) {
			if (movies.get(i).isFavourite())
				shownMovies.add(movies.get(i));
		}

		sortMovies();

		notifyDataSetChanged();

		hideProgressBar();
	}

	private void showAvailableFiles() {
		showProgressBar();

		shownMovies.clear();

		new Thread() {
			@Override
			public void run() {

				ArrayList<MediumMovie> tempMovies = new ArrayList<MediumMovie>();

				ArrayList<FileSource> filesources = MizLib.getFileSources(MizLib.TYPE_MOVIE, true);

				for (int i = 0; i < movies.size(); i++) {
					if (movies.get(i).isNetworkFile()) {
						if (MizLib.isWifiConnected(getActivity(), prefsDisableEthernetWiFiCheck)) {
							FileSource source = null;

							for (int j = 0; j < filesources.size(); j++)
								if (movies.get(i).getFilepath().contains(filesources.get(j).getFilepath())) {
									source = filesources.get(j);
									continue;
								}

							if (source == null)
								continue;

							try {
								final SmbFile file = new SmbFile(
										MizLib.createSmbLoginString(
												URLEncoder.encode(source.getDomain(), "utf-8"),
												URLEncoder.encode(source.getUser(), "utf-8"),
												URLEncoder.encode(source.getPassword(), "utf-8"),
												movies.get(i).getFilepath(),
												false
												));
								if (file.exists())
									tempMovies.add(movies.get(i));
							} catch (Exception e) {}  // Do nothing - the file isn't available (either MalformedURLException or SmbException)
						}
					} else {
						if (new File(movies.get(i).getFilepath()).exists())
							tempMovies.add(movies.get(i));
					}
				}

				// Check if "Available files" is still selected
				if (isAdded())
					if (getActivity().getActionBar().getSelectedNavigationIndex() == 2) {

						shownMovies.addAll(tempMovies);

						// Clean up...
						tempMovies.clear();
						tempMovies = null;

						sortMovies();

						getActivity().runOnUiThread(new Runnable() {
							@Override
							public void run() {
								notifyDataSetChanged();

								hideProgressBar();
							}	
						});
					}
			}
		}.start();
	}

	private void showCollections() {
		showProgressBar();

		shownMovies.clear();
		HashMap<String, MediumMovie> map = new HashMap<String, MediumMovie>();
		for (MediumMovie m : movies) {
			if (!MizLib.isEmpty(m.getCollection()) && !m.getCollection().equals("null")) {
				if (!map.containsKey(m.getCollection())) {
					map.put(m.getCollection(), m);
					shownMovies.add(m);
				}
			}
		}
		map.clear();

		sortMovies();

		notifyDataSetChanged();

		hideProgressBar();
	}

	private void showWatchedMovies(boolean watched) {
		showProgressBar();

		shownMovies.clear();

		for (int i = 0; i < movies.size(); i++) {
			if (watched) {
				if (movies.get(i).hasWatched())
					shownMovies.add(movies.get(i));
			} else {
				if (!movies.get(i).hasWatched())
					shownMovies.add(movies.get(i));
			}
		}

		sortMovies();

		notifyDataSetChanged();

		hideProgressBar();
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.menu, menu);
		if (type == OTHER) // Don't show the Update icon if this is the Watchlist
			menu.removeItem(R.id.update);
		SearchView searchView = (SearchView) menu.findItem(R.id.search_textbox).getActionView();
		searchView.setOnQueryTextListener(new OnQueryTextListener() {
			@Override
			public boolean onQueryTextChange(String newText) {
				if (newText.length() > 0) {
					search(newText);
				} else {
					showAllMovies();
				}
				return true;
			}
			@Override
			public boolean onQueryTextSubmit(String query) { return false; }
		});
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {		
		super.onOptionsItemSelected(item);

		switch (item.getItemId()) {
		case R.id.update:
			startActivityForResult(getUpdateIntent(), 0);
			break;
		case R.id.menuSortAdded:
			sortByDateAdded();
			break;
		case R.id.menuSortRating:
			sortByRating();
			break;
		case R.id.menuSortWeightedRating:
			sortByWeightedRating();
			break;
		case R.id.menuSortRelease:
			sortByRelease();
			break;
		case R.id.menuSortTitle:
			sortByTitle();
			break;
		case R.id.menuSortDuration:
			sortByDuration();
			break;
		case R.id.menuSettings:
			startActivity(new Intent(getActivity(), Preferences.class));
			break;
		case R.id.menuContact:
			MizLib.contactDev(getActivity());
			break;
		case R.id.genres:
			showGenres();
			break;
		case R.id.certifications:
			showCertifications();
			break;
		case R.id.folders:
			showFolders();
			break;
		case R.id.fileSources:
			showFileSources();
			break;
		}

		return true;
	}

	private Intent getUpdateIntent() {
		Intent intent = new Intent();
		intent.setClass(getActivity(), Update.class);
		intent.putExtra("isMovie", true);
		return intent;
	}

	private void sortMovies() {
		String SORT_TYPE = settings.getString("prefsSorting", "sortTitle");
		if (SORT_TYPE.equals("sortTitle")) {
			sortByTitle();
		} else if (SORT_TYPE.equals("sortRelease")) {
			sortByRelease();
		} else if (SORT_TYPE.equals("sortRating")) {
			sortByRating();
		} else if (SORT_TYPE.equals("sortWeightedRating")) {
			sortByWeightedRating();
		} else if (SORT_TYPE.equals("sortAdded")) {
			sortByDateAdded();
		} else if (SORT_TYPE.equals("sortDuration")) {
			sortByDuration();
		} else {
			sortByTitle();
		}
	}

	public void sortByTitle() {
		Editor editor = settings.edit();
		editor.putString("prefsSorting", "sortTitle");
		editor.apply();

		sortBy(TITLE);
	}

	public void sortByRelease() {
		Editor editor = settings.edit();
		editor.putString("prefsSorting", "sortRelease");
		editor.apply();

		sortBy(RELEASE);
	}

	public void sortByRating() {
		Editor editor = settings.edit();
		editor.putString("prefsSorting", "sortRating");
		editor.apply();

		sortBy(RATING);
	}

	public void sortByWeightedRating() {
		Editor editor = settings.edit();
		editor.putString("prefsSorting", "sortWeightedRating");
		editor.apply();

		sortBy(WEIGHTED_RATING);
	}

	public void sortByDateAdded() {
		Editor editor = settings.edit();
		editor.putString("prefsSorting", "sortAdded");
		editor.apply();

		sortBy(DATE);
	}
	
	public void sortByDuration() {
		Editor editor = settings.edit();
		editor.putString("prefsSorting", "sortDuration");
		editor.apply();

		sortBy(DURATION);
	}

	private final int TITLE = 10, RELEASE = 11, RATING = 12, DATE = 13, WEIGHTED_RATING = 14, DURATION = 15;

	public void sortBy(int sort) {

		ArrayList<MediumMovie> tempMovies = new ArrayList<MediumMovie>(shownMovies);

		switch (sort) {	
		case TITLE:

			Collections.sort(tempMovies, new Comparator<MediumMovie>() {
				@Override
				public int compare(MediumMovie o1, MediumMovie o2) {
					return o1.getTitle().compareToIgnoreCase(o2.getTitle());
				}
			});

			break;
		case RELEASE:

			Collections.sort(tempMovies, new Comparator<MediumMovie>() {
				@Override
				public int compare(MediumMovie o1, MediumMovie o2) {
					// Dates are always presented as YYYY-MM-DD, so removing
					// the hyphens will easily provide a great way of sorting.

					int firstDate = 0, secondDate = 0;
					String first = "", second = "";

					if (o1.getReleasedate() != null)
						first = o1.getReleasedate().replace("-", "");

					if (!(first.equals("null") | first.isEmpty()))
						firstDate = Integer.valueOf(first);

					if (o2.getReleasedate() != null)
						second = o2.getReleasedate().replace("-", "");

					if (!(second.equals("null") | second.isEmpty()))
						secondDate = Integer.valueOf(second);

					// This part is reversed to get the highest numbers first
					if (firstDate < secondDate)
						return 1; // First date is lower than second date - put it second
					else if (firstDate > secondDate)
						return -1; // First date is greater than second date - put it first

					return 0; // They're equal
				}
			});

			break;
		case RATING:

			Collections.sort(tempMovies, new Comparator<MediumMovie>() {
				@Override
				public int compare(MediumMovie o1, MediumMovie o2) {	
					if (o1.getRawRating() < o2.getRawRating())
						return 1;
					else if (o1.getRawRating() > o2.getRawRating())
						return -1;

					return 0;
				}
			});

			break;
		case WEIGHTED_RATING:
			Collections.sort(tempMovies, new Comparator<MediumMovie>() {
				@Override
				public int compare(MediumMovie o1, MediumMovie o2) {	
					if (o1.getWeightedRating() < o2.getWeightedRating())
						return 1;
					else if (o1.getWeightedRating() > o2.getWeightedRating())
						return -1;

					return 0;
				}
			});
			break;
		case DATE:

			Collections.sort(tempMovies, new Comparator<MediumMovie>() {
				@Override
				public int compare(MediumMovie o1, MediumMovie o2) {		
					return o1.getDateAdded().compareTo(o2.getDateAdded()) * -1;
				}
			});

			break;
		case DURATION:
			
			Collections.sort(tempMovies, new Comparator<MediumMovie>() {
				@Override
				public int compare(MediumMovie o1, MediumMovie o2) {
					
					int first = Integer.valueOf(o1.getRuntime());
					int second = Integer.valueOf(o2.getRuntime());
					
					if (first < second)
						return 1;
					else if (first > second)
						return -1;

					return 0;
				}
			});
		}

		shownMovies = new ArrayList<MediumMovie>(tempMovies);

		// Clean up on aisle three...
		tempMovies.clear();
		tempMovies = null;
	}

	private void showGenres() {
		showCollectionBasedOnNavigationIndex(actionBar.getSelectedNavigationIndex());

		final TreeMap<String, Integer> map = new TreeMap<String, Integer>();
		for (int i = 0; i < shownMovies.size(); i++) {
			if (!shownMovies.get(i).getGenres().isEmpty())
				for (String genre : shownMovies.get(i).getGenres().split(",")) {	
					if (map.containsKey(genre.trim())) {
						map.put(genre.trim(), map.get(genre.trim()) + 1);
					} else {
						map.put(genre.trim(), 1);
					}
				}
		}

		final CharSequence[] tempArray = map.keySet().toArray(new CharSequence[map.keySet().size()]);	
		for (int i = 0; i < tempArray.length; i++)
			tempArray[i] = tempArray[i] + " (" + map.get(tempArray[i]) +  ")";

		final CharSequence[] temp = new CharSequence[tempArray.length + 1];
		temp[0] = getString(R.string.allGenres);

		for (int i = 1; i < temp.length; i++)
			temp[i] = tempArray[i-1];


		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(R.string.selectGenre)
		.setItems(temp, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				if (which > 0) {
					ArrayList<MediumMovie> currentlyShown = new ArrayList<MediumMovie>();
					currentlyShown.addAll(shownMovies);

					shownMovies.clear();

					String selectedGenre = temp[which].toString();
					selectedGenre = selectedGenre.substring(0, selectedGenre.lastIndexOf("(")).trim();

					for (int i = 0; i < currentlyShown.size(); i++)
						if (currentlyShown.get(i).getGenres().contains(selectedGenre))
							shownMovies.add(currentlyShown.get(i));

					sortMovies();
					notifyDataSetChanged();
				}

				dialog.dismiss();
			}
		});
		builder.show();
	}

	private void showCertifications() {
		showCollectionBasedOnNavigationIndex(actionBar.getSelectedNavigationIndex());

		final TreeMap<String, Integer> map = new TreeMap<String, Integer>();
		for (int i = 0; i < shownMovies.size(); i++) {
			String certification = shownMovies.get(i).getCertification();
			if (!MizLib.isEmpty(certification)) {
				if (map.containsKey(certification.trim())) {
					map.put(certification.trim(), map.get(certification.trim()) + 1);
				} else {
					map.put(certification.trim(), 1);
				}
			}
		}

		final CharSequence[] tempArray = map.keySet().toArray(new CharSequence[map.keySet().size()]);	
		for (int i = 0; i < tempArray.length; i++)
			tempArray[i] = tempArray[i] + " (" + map.get(tempArray[i]) +  ")";

		final CharSequence[] temp = new CharSequence[tempArray.length + 1];
		temp[0] = getString(R.string.allCertifications);

		for (int i = 1; i < temp.length; i++)
			temp[i] = tempArray[i-1];


		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(R.string.selectCertification)
		.setItems(temp, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				if (which > 0) {
					ArrayList<MediumMovie> currentlyShown = new ArrayList<MediumMovie>();
					currentlyShown.addAll(shownMovies);

					shownMovies.clear();

					String selectedGenre = temp[which].toString();
					selectedGenre = selectedGenre.substring(0, selectedGenre.lastIndexOf("(")).trim();

					for (int i = 0; i < currentlyShown.size(); i++)
						if (currentlyShown.get(i).getCertification().trim().contains(selectedGenre))
							shownMovies.add(currentlyShown.get(i));

					sortMovies();
					notifyDataSetChanged();
				}

				dialog.dismiss();
			}
		});
		builder.show();
	}

	private void showFileSources() {
		ArrayList<FileSource> sources = new ArrayList<FileSource>();

		DbAdapterSources dbHelper = MizuuApplication.getSourcesAdapter();
		Cursor cursor = dbHelper.fetchAllMovieSources();
		while (cursor.moveToNext()) {
			sources.add(new FileSource(
					cursor.getLong(cursor.getColumnIndex(DbAdapterSources.KEY_ROWID)),
					cursor.getString(cursor.getColumnIndex(DbAdapterSources.KEY_FILEPATH)),
					cursor.getInt(cursor.getColumnIndex(DbAdapterSources.KEY_IS_SMB)),
					cursor.getString(cursor.getColumnIndex(DbAdapterSources.KEY_USER)),
					cursor.getString(cursor.getColumnIndex(DbAdapterSources.KEY_PASSWORD)),
					cursor.getString(cursor.getColumnIndex(DbAdapterSources.KEY_DOMAIN)),
					cursor.getString(cursor.getColumnIndex(DbAdapterSources.KEY_TYPE))
					));
		}
		cursor.close();

		showCollectionBasedOnNavigationIndex(actionBar.getSelectedNavigationIndex());

		final TreeMap<String, Integer> map = new TreeMap<String, Integer>();

		for (int i = 0; i < shownMovies.size(); i++) {
			String filepath = shownMovies.get(i).getFilepath();
			for (int j = 0; j < sources.size(); j++) {
				String source = sources.get(j).getFilepath();

				if (!MizLib.isEmpty(source) && !MizLib.isEmpty(filepath) && filepath.contains(source)) {
					if (map.containsKey(source.trim())) {
						map.put(source.trim(), map.get(source.trim()) + 1);
					} else {
						map.put(source.trim(), 1);
					}
				}
			}
		}

		final CharSequence[] tempArray = map.keySet().toArray(new CharSequence[map.keySet().size()]);	
		for (int i = 0; i < tempArray.length; i++)
			tempArray[i] = tempArray[i] + " (" + map.get(tempArray[i]) +  ")";

		final CharSequence[] temp = new CharSequence[tempArray.length + 1];
		temp[0] = getString(R.string.allFileSources);

		for (int i = 1; i < temp.length; i++)
			temp[i] = tempArray[i-1];


		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(R.string.selectFileSource)
		.setItems(temp, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				if (which > 0) {
					ArrayList<MediumMovie> currentlyShown = new ArrayList<MediumMovie>();
					currentlyShown.addAll(shownMovies);

					shownMovies.clear();

					String selectedFolder = temp[which].toString();
					selectedFolder = selectedFolder.substring(0, selectedFolder.lastIndexOf("(")).trim();

					for (int i = 0; i < currentlyShown.size(); i++)
						if (currentlyShown.get(i).getFilepath().trim().contains(selectedFolder))
							shownMovies.add(currentlyShown.get(i));

					sortMovies();
					notifyDataSetChanged();

					int size = shownMovies.size();
					if (actionBar.getSelectedNavigationIndex() == 3) // Collections
						actionBar.setSubtitle(size + " " + getResources().getQuantityString(R.plurals.collectionsInLibrary, size, size));
					else
						actionBar.setSubtitle(size + " " + getResources().getQuantityString(R.plurals.moviesInLibrary, size, size));
				}

				dialog.dismiss();
			}
		});
		builder.show();
	}

	private void showFolders() {
		showCollectionBasedOnNavigationIndex(actionBar.getSelectedNavigationIndex());

		final TreeMap<String, Integer> map = new TreeMap<String, Integer>();
		for (int i = 0; i < shownMovies.size(); i++) {
			String folder = MizLib.getParentFolder(shownMovies.get(i).getFilepath());
			if (!MizLib.isEmpty(folder)) {
				if (map.containsKey(folder.trim())) {
					map.put(folder.trim(), map.get(folder.trim()) + 1);
				} else {
					map.put(folder.trim(), 1);
				}
			}
		}

		final CharSequence[] tempArray = map.keySet().toArray(new CharSequence[map.keySet().size()]);	
		for (int i = 0; i < tempArray.length; i++)
			tempArray[i] = tempArray[i] + " (" + map.get(tempArray[i]) +  ")";

		final CharSequence[] temp = new CharSequence[tempArray.length + 1];
		temp[0] = getString(R.string.allFolders);

		for (int i = 1; i < temp.length; i++)
			temp[i] = tempArray[i-1];


		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(R.string.selectFolder)
		.setItems(temp, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				if (which > 0) {
					ArrayList<MediumMovie> currentlyShown = new ArrayList<MediumMovie>();
					currentlyShown.addAll(shownMovies);

					shownMovies.clear();

					String selectedFolder = temp[which].toString();
					selectedFolder = selectedFolder.substring(0, selectedFolder.lastIndexOf("(")).trim();

					for (int i = 0; i < currentlyShown.size(); i++)
						if (currentlyShown.get(i).getFilepath().trim().startsWith(selectedFolder))
							shownMovies.add(currentlyShown.get(i));

					sortMovies();
					notifyDataSetChanged();
				}

				dialog.dismiss();
			}
		});
		builder.show();
	}

	private SearchTask mSearch;

	private void search(String query) {
		showProgressBar();

		if (mSearch != null)
			mSearch.cancel(true);

		mSearch = new SearchTask(query);
		mSearch.execute();
	}

	private class SearchTask extends AsyncTask<String, String, String> {

		private String searchQuery = "";

		public SearchTask(String query) {
			searchQuery = query.toLowerCase(Locale.ENGLISH);
		}

		@Override
		protected String doInBackground(String... params) {
			shownMovies.clear();

			if (searchQuery.startsWith("actor:")) {
				for (int i = 0; i < movies.size(); i++) {
					if (isCancelled())
						return null;

					if (movies.get(i).getCast().toLowerCase(Locale.ENGLISH).contains(searchQuery.replace("actor:", "").trim()))
						shownMovies.add(movies.get(i));
				}
			} else if (searchQuery.equalsIgnoreCase("show_duplicates")) {
				Set<String> f = new HashSet<String>(), dupes = new HashSet<String>();
				for (int i = 0; i < movies.size(); i++) {
					if (isCancelled())
						return null;

					if (!f.add(movies.get(i).getTmdbId()))
						dupes.add(movies.get(i).getTmdbId());
				}

				for (int i = 0; i < movies.size(); i++) {
					if (isCancelled())
						return null;

					for (String s : dupes)
						if (s.equals(movies.get(i).getTmdbId()))
							shownMovies.add(movies.get(i));
				}

				f.clear();
				f = null;
				dupes.clear();
				dupes = null;
			} else if (searchQuery.equalsIgnoreCase("missing_genres")) {
				for (int i = 0; i < movies.size(); i++) {
					if (isCancelled())
						return null;

					if (MizLib.isEmpty(movies.get(i).getGenres()))
						shownMovies.add(movies.get(i));
				}
			} else {					
				for (int i = 0; i < movies.size(); i++) {
					if (isCancelled())
						return null;

					if (movies.get(i).getTitle().toLowerCase(Locale.ENGLISH).indexOf(searchQuery) != -1 || movies.get(i).getFilepath().toLowerCase(Locale.ENGLISH).indexOf(searchQuery) != -1)
						shownMovies.add(movies.get(i));
				}
			}

			sortMovies();

			return null;
		}

		@Override
		protected void onPostExecute(String result) {
			notifyDataSetChanged();
			hideProgressBar();
		}

	}

	private void showProgressBar() {
		pbar.setVisibility(View.VISIBLE);
		mGridView.setVisibility(View.GONE);
	}

	private void hideProgressBar() {
		pbar.setVisibility(View.GONE);
		mGridView.setVisibility(View.VISIBLE);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (resultCode == 1) { // Update
			forceLoaderLoad();
		} else if (resultCode == 2) { // Favourite removed
			if (actionBar.getSelectedNavigationIndex() == 1) {
				showFavorites();
			}
		} else if (resultCode == 3) {
			notifyDataSetChanged();
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals("prefsIgnorePrefixesInTitles")) {
			ignorePrefixes = settings.getBoolean("prefsIgnorePrefixesInTitles", false);
			forceLoaderLoad();
		}

		showGridTitles = settings.getBoolean("prefsShowGridTitles", false);

		sortMovies();
		notifyDataSetChanged();
	}

	private void forceLoaderLoad() {
		if (getLoaderManager().getLoader(0) == null)
			getLoaderManager().initLoader(0, null, loaderCallbacks);
		else
			getLoaderManager().restartLoader(0, null, loaderCallbacks);
	}

	private class ActionBarSpinner extends BaseAdapter {

		private LayoutInflater inflater;

		public ActionBarSpinner() {
			inflater = LayoutInflater.from(getActivity());
		}

		@Override
		public int getCount() {
			return spinnerItems.size();
		}

		@Override
		public Object getItem(int position) {
			return null;
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public int getItemViewType(int position) {
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			convertView = inflater.inflate(R.layout.spinner_header, parent, false);
			((TextView) convertView.findViewById(R.id.title)).setText(spinnerItems.get(position).getTitle());

			int size = shownMovies.size();
			if (actionBar.getSelectedNavigationIndex() == 3) // Collections
				((TextView) convertView.findViewById(R.id.subtitle)).setText(size + " " + getResources().getQuantityString(R.plurals.collectionsInLibrary, size, size));
			else
				((TextView) convertView.findViewById(R.id.subtitle)).setText(size + " " + getResources().getQuantityString(R.plurals.moviesInLibrary, size, size));

			return convertView;
		}

		@Override
		public int getViewTypeCount() {
			return 0;
		}

		@Override
		public boolean hasStableIds() {
			return true;
		}

		@Override
		public boolean isEmpty() {
			return spinnerItems.size() == 0;
		}

		@Override
		public View getDropDownView(int position, View convertView, ViewGroup parent) {			
			convertView = inflater.inflate(android.R.layout.simple_spinner_dropdown_item, parent, false);
			((TextView) convertView.findViewById(android.R.id.text1)).setText(spinnerItems.get(position).getSubtitle());

			return convertView;
		}
	}
}